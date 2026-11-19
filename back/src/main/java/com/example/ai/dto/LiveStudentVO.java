package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveStudentVO {

    private Long userId;
    private String name;
    private String studentNo;
    private Boolean online;
    private Boolean checked;
    private Boolean late;
    private LocalDateTime lastHeartbeat;
    private LocalDateTime checkinTime;
}