package com.example.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class QuizSubmitDTO {

    private Long quizId;

    private Long assignmentId;

    private List<QuizAnswerDTO> answers;
}