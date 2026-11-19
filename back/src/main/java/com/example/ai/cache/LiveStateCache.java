package com.example.ai.cache;

public interface LiveStateCache {

    LiveStateSnapshot get(Long sessionId);

    void put(Long sessionId, LiveStateSnapshot snapshot);

    void evict(Long sessionId);

    void evictAll();
}