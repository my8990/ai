package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class StudentScoreVO {

    private Long studentId;

    private String studentName;

    private String studentNo;

    private Integer checkinCount;

    private Integer totalCheckin;

    private BigDecimal checkinScore;

    private BigDecimal homeworkScore;

    private Integer quizCount;

    private Integer totalQuiz;

    private BigDecimal quizScore;

    private BigDecimal totalScore;

    private Integer rank;

    private Integer checkinWeight;

    private Integer homeworkWeight;

    private Integer quizWeight;
}