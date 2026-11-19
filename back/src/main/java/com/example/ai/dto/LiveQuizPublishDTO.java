package com.example.ai.dto;

import lombok.Data;

@Data
public class LiveQuizPublishDTO {

    private Long quizId;

    private String title;

    private Integer durationMinutes;
}