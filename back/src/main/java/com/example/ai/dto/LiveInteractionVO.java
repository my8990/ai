package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveInteractionVO {

    private Long id;
    private Long sessionId;
    private Long userId;
    private String name;
    private String studentNo;
    private String type;
    private String content;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime handleTime;
}