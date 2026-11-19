package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class QuizQuestionVO {

    private Long id;

    private Integer type;

    private String content;

    private List<String> options;

    private BigDecimal score;

    private Integer sortOrder;

    private String correctAnswer;

    private String analysis;

    private String myAnswer;

    private Boolean correct;
}