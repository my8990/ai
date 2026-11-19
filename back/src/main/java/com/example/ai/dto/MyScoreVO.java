package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class MyScoreVO {

    private Long courseId;

    private String courseName;

    private Integer checkinWeight;

    private Integer homeworkWeight;

    private Integer quizWeight;

    private Integer checkinCount;

    private Integer totalCheckin;

    private BigDecimal checkinScore;

    private BigDecimal homeworkScore;

    private Integer quizCount;

    private Integer totalQuiz;

    private BigDecimal quizScore;

    private BigDecimal totalScore;
}