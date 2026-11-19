package com.example.ai.websocket;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.entity.LiveBroadcastEvent;
import com.example.ai.mapper.LiveBroadcastEventMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.live.broadcast.mode", havingValue = "db", matchIfMissing = true)
public class DbBroadcastBus implements LiveBroadcastBus {

    private static final long BROADCAST_INTERVAL_MS = 3000;

    private static final String INSTANCE_ID = UUID.randomUUID().toString().replace("-", "");

    private static final int EVENT_BATCH_SIZE = 200;

    private final LiveBroadcastEventMapper eventMapper;

    private final Map<Long, Long> lastBroadcast = new ConcurrentHashMap<>();
    private final AtomicLong lastEventId = new AtomicLong(0L);

    private volatile boolean eventCursorReady = false;

    private volatile LocalPusher localPusher;

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
        publishEvent(sessionId, true);
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
        publishEvent(sessionId, false);
    }

    @Override
    public void clear(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        lastBroadcast.remove(sessionId);
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

    private void publishEvent(Long sessionId, boolean forceRefresh) {
        try {
            LiveBroadcastEvent event = new LiveBroadcastEvent();
            event.setSessionId(sessionId);
            event.setForceRefresh(forceRefresh ? 1 : 0);
            event.setInstanceId(INSTANCE_ID);
            eventMapper.insert(event);
        } catch (Exception e) {
            log.debug("写入课堂广播事件失败: {}", e.getMessage());
        }
    }

    @Scheduled(fixedDelayString = "${app.live.broadcast.poll-interval-ms:500}")
    public void pollRemoteEvents() {
        if (!eventCursorReady) {
            LiveBroadcastEvent latest = eventMapper.selectOne(
                    new LambdaQueryWrapper<LiveBroadcastEvent>()
                            .orderByDesc(LiveBroadcastEvent::getId)
                            .last("limit 1")
            );
            lastEventId.set(latest == null ? 0L : latest.getId());
            eventCursorReady = true;
            return;
        }
        long since = lastEventId.get();
        List<LiveBroadcastEvent> events;
        try {
            events = eventMapper.selectList(
                    new LambdaQueryWrapper<LiveBroadcastEvent>()
                            .gt(LiveBroadcastEvent::getId, since)
                            .orderByAsc(LiveBroadcastEvent::getId)
                            .last("limit " + EVENT_BATCH_SIZE)
            );
        } catch (Exception e) {
            log.debug("读取课堂广播事件失败: {}", e.getMessage());
            return;
        }
        if (events.isEmpty()) {
            return;
        }
        Map<Long, Boolean> pending = new LinkedHashMap<>();
        long maxId = since;
        for (LiveBroadcastEvent event : events) {
            maxId = Math.max(maxId, event.getId());
            if (INSTANCE_ID.equals(event.getInstanceId())) {
                continue;
            }
            if (event.getSessionId() == null) {
                continue;
            }
            boolean force = event.getForceRefresh() != null && event.getForceRefresh() == 1;
            Boolean existing = pending.get(event.getSessionId());
            pending.put(event.getSessionId(), (existing != null && existing) || force);
        }
        lastEventId.set(maxId);
        for (Map.Entry<Long, Boolean> entry : pending.entrySet()) {
            if (pushLocal(entry.getKey(), entry.getValue())) {
                lastBroadcast.put(entry.getKey(), System.currentTimeMillis());
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.live.broadcast.event-sweep-interval-ms:300000}")
    public void sweepBroadcastEvents() {
        try {
            eventMapper.delete(
                    new LambdaQueryWrapper<LiveBroadcastEvent>()
                            .lt(LiveBroadcastEvent::getCreateTime, LocalDateTime.now().minusMinutes(10))
            );
        } catch (Exception e) {
            log.debug("清理课堂广播事件失败: {}", e.getMessage());
        }
    }
}