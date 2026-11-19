package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class NoticeVO {

    private Long id;

    private Long courseId;

    private String courseName;

    private Integer type;

    private String title;

    private String content;

    private Long sessionId;

    private Long materialId;

    private Long teacherId;

    private String teacherName;

    private LocalDateTime createTime;
}