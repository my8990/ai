package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class QuizQuestionDTO {

    private Integer type;

    private String content;

    private List<String> options;

    private String correctAnswer;

    private BigDecimal score;

    private String analysis;

    private Integer sortOrder;
}