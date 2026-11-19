package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CheckinSubmitByCodeDTO {

    @NotBlank(message = "签到码不能为空")
    private String code;

    private String source;

    private String token;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String address;
}