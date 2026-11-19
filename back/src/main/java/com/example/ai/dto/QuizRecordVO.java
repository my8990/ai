package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class QuizRecordVO {

    private Long recordId;

    private Long quizId;

    private Long assignmentId;

    private String quizTitle;

    private String courseName;

    private BigDecimal score;

    private BigDecimal fullScore;

    private Integer status;

    private String statusText;

    private LocalDateTime submitAt;

    private Boolean showAnswer;

    private List<QuizQuestionVO> questions;
}