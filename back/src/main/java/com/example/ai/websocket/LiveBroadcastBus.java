package com.example.ai.websocket;

public interface LiveBroadcastBus {

    void publish(Long sessionId);

    void publishThrottled(Long sessionId);

    void registerLocalPusher(LocalPusher pusher);

    void clear(Long sessionId);
}