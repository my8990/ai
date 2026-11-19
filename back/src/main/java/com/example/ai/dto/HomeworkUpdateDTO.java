package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class HomeworkUpdateDTO {

    private Long id;

    private String title;

    private String description;

    private BigDecimal fullScore;

    private String refFileUrl;

    private String refFileName;

    private String deadline;
}