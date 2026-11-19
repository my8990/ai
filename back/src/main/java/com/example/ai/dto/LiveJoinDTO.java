package com.example.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class LiveJoinDTO {

    @NotNull(message = "课堂ID不能为空")
    private Long sessionId;

    private String code;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String address;
}