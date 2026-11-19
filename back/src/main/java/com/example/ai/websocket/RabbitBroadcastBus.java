package com.example.ai.websocket;

import com.example.ai.mq.LiveBroadcastMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.live.broadcast.mode", havingValue = "rabbit")
public class RabbitBroadcastBus implements LiveBroadcastBus {

    private static final long BROADCAST_INTERVAL_MS = 3000;

    private static final String INSTANCE_ID = UUID.randomUUID().toString().replace("-", "");

    private final RabbitTemplate rabbitTemplate;

    private final Map<Long, Long> lastBroadcast = new ConcurrentHashMap<>();

    private volatile LocalPusher localPusher;

    @Value("${app.live.broadcast.exchange}")
    private String exchange;

    @Value("${app.live.broadcast.routing-key}")
    private String routingKey;

    @Override
    public void registerLocalPusher(LocalPusher pusher) {
        this.localPusher = pusher;
    }

    @Override
    public void publish(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        if (pushLocal(sessionId, true)) {
            lastBroadcast.put(sessionId, System.currentTimeMillis());
        }
        send(sessionId, true);
    }

    @Override
    public void publishThrottled(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        Long last = lastBroadcast.get(sessionId);
        long now = System.currentTimeMillis();
        if (last != null && now - last < BROADCAST_INTERVAL_MS) {
            return;
        }
        if (pushLocal(sessionId, false)) {
            lastBroadcast.put(sessionId, System.currentTimeMillis());
        }
        send(sessionId, false);
    }

    @Override
    public void clear(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        lastBroadcast.remove(sessionId);
    }

    @RabbitListener(queues = "${app.live.broadcast.queue}")
    public void onBroadcastEvent(LiveBroadcastMessage message) {
        if (message == null || message.getSessionId() == null) {
            return;
        }
        if (INSTANCE_ID.equals(message.getInstanceId())) {
            return;
        }
        if (pushLocal(message.getSessionId(), message.isForceRefresh())) {
            lastBroadcast.put(message.getSessionId(), System.currentTimeMillis());
        }
    }

    private boolean pushLocal(Long sessionId, boolean forceRefresh) {
        LocalPusher pusher = localPusher;
        if (pusher == null) {
            return false;
        }
        try {
            return pusher.push(sessionId, forceRefresh);
        } catch (Exception e) {
            log.debug("课堂本地推送失败: {}", e.getMessage());
            return false;
        }
    }

    private void send(Long sessionId, boolean forceRefresh) {
        try {
            rabbitTemplate.convertAndSend(exchange, routingKey,
                    new LiveBroadcastMessage(sessionId, forceRefresh, INSTANCE_ID));
        } catch (Exception e) {
            log.warn("课堂广播消息投递失败，仅本实例推送: sessionId={}, reason={}", sessionId, e.getMessage());
        }
    }
}