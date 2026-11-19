package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class HomeworkVO {

    private Long id;

    private Long courseId;

    private String courseName;

    private String teacherName;

    private String title;

    private String description;

    private java.math.BigDecimal fullScore;

    private String refFileUrl;

    private String refFileName;

    private LocalDateTime deadline;

    private Long teacherId;

    private LocalDateTime createTime;

    private Integer submitCount;

    private Integer totalStudents;

    private Boolean expired;

    private Boolean submitted;

    private Boolean graded;

    private Boolean isLate;

    private java.math.BigDecimal myScore;
}