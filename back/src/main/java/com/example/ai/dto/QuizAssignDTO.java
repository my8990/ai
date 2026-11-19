package com.example.ai.dto;

import lombok.Data;

@Data
public class QuizAssignDTO {

    private Long id;

    private Long quizId;

    private Long courseId;

    private String title;

    private Integer durationMinutes;

    private String startTime;

    private String endTime;

    private Boolean publishNow;
}