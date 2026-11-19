package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuizVO {

    private Long id;

    private Long courseId;

    private String courseName;

    private String teacherName;

    private String title;

    private String description;

    private BigDecimal fullScore;

    private Integer durationMinutes;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Boolean showAnswerAfter;

    private Integer status;

    private String statusText;

    private Integer questionCount;

    private LocalDateTime createTime;

    private Boolean canTake;

    private Boolean taken;

    private BigDecimal myScore;

    private List<QuizQuestionVO> questions;

    private Long assignmentId;

    private Integer assignmentStatus;

    private String assignmentStatusText;

    private Integer visibility;

    private Boolean shared;

    private Boolean favorited;

    private Integer favoriteCount;

    private Integer useCount;

    private Long sourceQuizId;

    private Boolean mine;

    private String authorName;

    private Boolean canEdit;

    private Boolean canEnd;

    private Boolean canAssign;
}