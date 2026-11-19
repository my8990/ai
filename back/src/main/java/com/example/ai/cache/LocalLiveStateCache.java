package com.example.ai.cache;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.live.cache.mode", havingValue = "local", matchIfMissing = true)
public class LocalLiveStateCache implements LiveStateCache {

    private final long ttlMillis;

    private final Map<Long, Holder> cache = new ConcurrentHashMap<>();

    public LocalLiveStateCache(@Value("${app.live.cache.state-ttl-ms:2000}") long ttlMillis) {
        this.ttlMillis = Math.max(0L, ttlMillis);
    }

    @Override
    public LiveStateSnapshot get(Long sessionId) {
        if (sessionId == null || ttlMillis <= 0) {
            return null;
        }
        Holder holder = cache.get(sessionId);
        if (holder == null) {
            return null;
        }
        if (holder.expireAt <= System.currentTimeMillis()) {
            cache.remove(sessionId, holder);
            return null;
        }
        return holder.snapshot;
    }

    @Override
    public void put(Long sessionId, LiveStateSnapshot snapshot) {
        if (sessionId == null || snapshot == null || ttlMillis <= 0) {
            return;
        }
        cache.put(sessionId, new Holder(snapshot, System.currentTimeMillis() + ttlMillis));
    }

    @Override
    public void evict(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        cache.remove(sessionId);
    }

    @Override
    public void evictAll() {
        cache.clear();
    }

    @Scheduled(fixedDelayString = "${app.live.cache.sweep-interval-ms:60000}")
    public void sweep() {
        if (cache.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        int removed = 0;
        Iterator<Map.Entry<Long, Holder>> iterator = cache.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Long, Holder> entry = iterator.next();
            if (entry.getValue().expireAt <= now) {
                iterator.remove();
                removed++;
            }
        }
        if (removed > 0) {
            log.debug("课堂状态快照过期清理: {}", removed);
        }
    }

    private static class Holder {

        private final LiveStateSnapshot snapshot;

        private final long expireAt;

        private Holder(LiveStateSnapshot snapshot, long expireAt) {
            this.snapshot = snapshot;
            this.expireAt = expireAt;
        }
    }
}