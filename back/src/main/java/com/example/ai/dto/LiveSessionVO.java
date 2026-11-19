package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class LiveSessionVO {

    private Long id;
    private Long courseId;
    private String courseName;
    private Long teacherId;
    private String teacherName;
    private Long checkinId;
    private String checkinCode;
    private String joinCode;
    private String title;
    private Integer currentPage;
    private Integer slideTotal;
    private Integer status;
    private Integer checkedCount;
    private Integer totalCount;
    private Boolean checked;
    private String qrContent;
    private LocalDateTime createTime;
    private LocalDateTime endTime;
    private Long durationMinutes;
}