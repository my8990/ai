package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AuditLogVO {

    private Long id;
    private Long userId;
    private String username;
    private String role;
    private String module;
    private String action;
    private String method;
    private String uri;
    private Integer httpStatus;
    private Integer success;
    private String errorMessage;
    private String ip;
    private Long durationMs;
    private String traceId;
    private LocalDateTime createTime;
}