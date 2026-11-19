package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class ScoreDetailExportVO {

    private String studentNo;

    private String studentName;

    private String type;

    private String title;

    private BigDecimal fullScore;

    private BigDecimal score;

    private BigDecimal rate;

    private String status;

    private String time;
}