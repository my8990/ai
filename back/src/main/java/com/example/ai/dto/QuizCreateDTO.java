package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class QuizCreateDTO {

    private Long id;

    private Long courseId;

    private String title;

    private String description;

    private BigDecimal fullScore;

    private Integer durationMinutes;

    private String startTime;

    private String endTime;

    private Boolean showAnswerAfter;

    private List<QuizQuestionDTO> questions;
}