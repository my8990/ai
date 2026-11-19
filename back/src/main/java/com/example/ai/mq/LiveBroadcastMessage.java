package com.example.ai.mq;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class LiveBroadcastMessage {

    private Long sessionId;

    private boolean forceRefresh;

    private String instanceId;
}