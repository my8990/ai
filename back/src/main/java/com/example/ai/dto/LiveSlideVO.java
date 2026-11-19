package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveSlideVO {

    private Long id;
    private Integer pageNo;
    private String imageUrl;
    private String fileName;
    private LocalDateTime createTime;
}