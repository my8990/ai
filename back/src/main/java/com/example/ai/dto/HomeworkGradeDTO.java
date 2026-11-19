package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class HomeworkGradeDTO {

    private Long submitId;

    private BigDecimal score;

    private String comment;
}