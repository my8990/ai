package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppealVO {

    private Long id;
    private Long userId;
    private String username;
    private String nickname;
    private String reason;
    private Integer status;
    private String reply;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}