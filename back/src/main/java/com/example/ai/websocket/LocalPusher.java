package com.example.ai.websocket;

@FunctionalInterface
public interface LocalPusher {

    boolean push(Long sessionId, boolean forceRefresh);
}