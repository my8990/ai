package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class LiveQuizVO {

    private Long assignmentId;

    private Long quizId;

    private String title;

    private Integer questionCount;

    private BigDecimal fullScore;

    private Integer durationMinutes;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    private String statusText;

    private Integer submittedCount;

    private Integer totalCount;

    private Boolean mySubmitted;

    private BigDecimal myScore;
}