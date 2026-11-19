package com.example.ai.websocket;

import com.example.ai.dto.LiveStateVO;
import com.example.ai.service.LiveService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.SessionLimitExceededException;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
public class LiveWebSocketHandler extends TextWebSocketHandler implements LocalPusher {

    private static final String ATTR_SESSION_ID = "liveSessionId";
    private static final String ATTR_USER_ID = "userId";
    private static final String ATTR_ROLE = "role";

    private static final int SEND_TIME_LIMIT_MS = 5000;

    private static final int SEND_BUFFER_SIZE_LIMIT = 512 * 1024;

    private final LiveService liveService;
    private final ObjectMapper objectMapper;
    private final LiveBroadcastBus broadcastBus;

    private final Map<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        broadcastBus.registerLocalPusher(this);
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Map<String, String> params = parseQuery(session.getUri());
        String ticket = params.get("ticket");
        if (ticket == null || ticket.isBlank()) {
            closeQuietly(session, CloseStatus.PROTOCOL_ERROR.withReason("missing ticket"));
            return;
        }
        try {
            LiveService.WsPrincipal principal = liveService.consumeWsTicket(ticket);
            if (principal == null) {
                closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("invalid or expired ticket"));
                return;
            }
            LiveStateVO state = liveService.getState(principal.sessionId(), principal.userId(), principal.role());
            session.getAttributes().put(ATTR_SESSION_ID, principal.sessionId());
            session.getAttributes().put(ATTR_USER_ID, principal.userId());
            session.getAttributes().put(ATTR_ROLE, principal.role());
            WebSocketSession decorated = new ConcurrentWebSocketSessionDecorator(
                    session, SEND_TIME_LIMIT_MS, SEND_BUFFER_SIZE_LIMIT);
            sessions.computeIfAbsent(principal.sessionId(), key -> ConcurrentHashMap.newKeySet()).add(decorated);
            sendState(decorated, state);
        } catch (Exception e) {
            log.warn("课堂 WebSocket 连接失败: {}", e.getMessage());
            closeQuietly(session, CloseStatus.POLICY_VIOLATION.withReason("unauthorized"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        Long sessionId = (Long) session.getAttributes().get(ATTR_SESSION_ID);
        Long userId = (Long) session.getAttributes().get(ATTR_USER_ID);
        String role = (String) session.getAttributes().get(ATTR_ROLE);
        if (sessionId == null || userId == null) {
            return;
        }
        String payload = message.getPayload() == null ? "" : message.getPayload().trim();
        if ("ping".equalsIgnoreCase(payload)) {
            try {
                liveService.heartbeat(sessionId, userId, role);
                broadcastThrottled(sessionId);
            } catch (Exception e) {
                log.debug("课堂心跳处理失败: {}", e.getMessage());
            }
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long sessionId = (Long) session.getAttributes().get(ATTR_SESSION_ID);
        if (sessionId == null) {
            return;
        }
        Set<WebSocketSession> set = sessions.get(sessionId);
        if (set != null) {
            set.removeIf(item -> !item.isOpen());
            if (set.isEmpty()) {
                sessions.remove(sessionId);
                broadcastBus.clear(sessionId);
            }
        }
        broadcastThrottled(sessionId);
    }

    public void broadcast(Long sessionId) {
        broadcastBus.publish(sessionId);
    }

    public void broadcastThrottled(Long sessionId) {
        broadcastBus.publishThrottled(sessionId);
    }

    @Override
    public boolean push(Long sessionId, boolean forceRefresh) {
        if (sessionId == null) {
            return false;
        }
        Set<WebSocketSession> set = sessions.get(sessionId);
        if (set == null || set.isEmpty()) {
            return false;
        }
        LiveStateVO state;
        try {
            state = resolveState(sessionId, forceRefresh);
        } catch (Exception e) {
            log.debug("课堂状态推送失败: {}", e.getMessage());
            return false;
        }
        if (state == null) {
            return false;
        }
        JsonNode baseData;
        String sharedPayload = null;
        try {
            baseData = objectMapper.valueToTree(state);
            if (state.getHandCount() == null || state.getHandCount() <= 0) {
                sharedPayload = objectMapper.writeValueAsString(Map.of("type", "state", "data", baseData));
            }
        } catch (Exception e) {
            log.warn("课堂状态序列化失败: {}", e.getMessage());
            return false;
        }
        Set<Long> handUserIds = null;
        for (WebSocketSession item : set) {
            if (!item.isOpen()) {
                set.remove(item);
                continue;
            }
            try {
                String payload = sharedPayload;
                if (payload == null) {
                    if (handUserIds == null) {
                        handUserIds = liveService.pendingHandUserIds(sessionId);
                    }
                    Long userId = (Long) item.getAttributes().get(ATTR_USER_ID);
                    ObjectNode data = ((ObjectNode) baseData).deepCopy();
                    data.put("myHandRaised", handUserIds.contains(userId));
                    payload = objectMapper.writeValueAsString(Map.of("type", "state", "data", data));
                }
                item.sendMessage(new TextMessage(payload));
            } catch (Exception e) {
                set.remove(item);
                if (e instanceof SessionLimitExceededException) {
                    log.warn("课堂 WebSocket 慢客户端已断开: sessionId={}", sessionId);
                }
                closeQuietly(item, CloseStatus.SERVER_ERROR);
            }
        }
        return true;
    }

    private LiveStateVO resolveState(Long sessionId, boolean forceRefresh) {
        if (forceRefresh) {
            return liveService.refreshPushState(sessionId);
        }
        return liveService.getPushState(sessionId);
    }

    private void sendState(WebSocketSession session, LiveStateVO state) {
        if (state == null) {
            return;
        }
        try {
            String payload = objectMapper.writeValueAsString(Map.of("type", "state", "data", state));
            session.sendMessage(new TextMessage(payload));
        } catch (Exception e) {
            log.debug("课堂初始状态推送失败: {}", e.getMessage());
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session.isOpen()) {
                session.close(status);
            }
        } catch (Exception e) {
            log.debug("关闭课堂 WebSocket 失败: {}", e.getMessage());
        }
    }

    private Map<String, String> parseQuery(URI uri) {
        Map<String, String> result = new HashMap<>();
        if (uri == null || uri.getQuery() == null) {
            return result;
        }
        for (String pair : uri.getQuery().split("&")) {
            int index = pair.indexOf('=');
            if (index < 0) {
                continue;
            }
            String key = URLDecoder.decode(pair.substring(0, index), StandardCharsets.UTF_8);
            String value = URLDecoder.decode(pair.substring(index + 1), StandardCharsets.UTF_8);
            result.put(key, value);
        }
        return result;
    }
}