package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class QuizTakeVO {

    private Long quizId;

    private Long assignmentId;

    private Long recordId;

    private String title;

    private String description;

    private Integer durationMinutes;

    private Integer remainingSeconds;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private java.math.BigDecimal fullScore;

    private java.util.List<QuizQuestionVO> questions;
}