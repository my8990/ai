package com.example.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuizAnswerDTO {

    private Long questionId;

    private String answer;
}