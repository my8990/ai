package com.example.ai.dto;

import lombok.Data;

@Data
public class ScoreRuleDTO {

    private Long courseId;

    private Integer checkinWeight;

    private Integer homeworkWeight;

    private Integer quizWeight;
}