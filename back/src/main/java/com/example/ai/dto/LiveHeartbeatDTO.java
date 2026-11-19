package com.example.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LiveHeartbeatDTO {

    @NotNull(message = "课堂ID不能为空")
    private Long sessionId;
}