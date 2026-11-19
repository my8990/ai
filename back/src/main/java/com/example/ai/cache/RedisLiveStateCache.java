package com.example.ai.cache;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@ConditionalOnProperty(name = "app.live.cache.mode", havingValue = "redis")
public class RedisLiveStateCache implements LiveStateCache {

    private static final String KEY_PREFIX = "live:state:";

    private static final int SCAN_BATCH = 200;

    private final StringRedisTemplate redisTemplate;

    private final ObjectMapper objectMapper;

    private final long ttlMillis;

    public RedisLiveStateCache(StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper,
                               @Value("${app.live.cache.state-ttl-ms:2000}") long ttlMillis) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.ttlMillis = Math.max(0L, ttlMillis);
    }

    @Override
    public LiveStateSnapshot get(Long sessionId) {
        if (sessionId == null || ttlMillis <= 0) {
            return null;
        }
        try {
            String json = redisTemplate.opsForValue().get(KEY_PREFIX + sessionId);
            if (json == null || json.isEmpty()) {
                return null;
            }
            return objectMapper.readValue(json, LiveStateSnapshot.class);
        } catch (Exception e) {
            log.warn("读取课堂状态快照失败，按未命中处理: sessionId={}, reason={}", sessionId, e.getMessage());
            return null;
        }
    }

    @Override
    public void put(Long sessionId, LiveStateSnapshot snapshot) {
        if (sessionId == null || snapshot == null || ttlMillis <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(KEY_PREFIX + sessionId,
                    objectMapper.writeValueAsString(snapshot), Duration.ofMillis(ttlMillis));
        } catch (Exception e) {
            log.warn("写入课堂状态快照失败: sessionId={}, reason={}", sessionId, e.getMessage());
        }
    }

    @Override
    public void evict(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        try {
            redisTemplate.delete(KEY_PREFIX + sessionId);
        } catch (Exception e) {
            log.warn("清除课堂状态快照失败: sessionId={}, reason={}", sessionId, e.getMessage());
        }
    }

    @Override
    public void evictAll() {
        ScanOptions options = ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(SCAN_BATCH).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            List<String> keys = new ArrayList<>();
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
            if (!keys.isEmpty()) {
                redisTemplate.delete(keys);
            }
        } catch (Exception e) {
            log.warn("批量清除课堂状态快照失败: {}", e.getMessage());
        }
    }
}