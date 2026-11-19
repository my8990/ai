package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class CheckinSubmitDTO {

    @NotNull(message = "签到活动ID不能为空")
    private Long checkinId;

    @NotBlank(message = "签到码不能为空")
    private String code;

    private String source;

    private String token;

    private BigDecimal latitude;

    private BigDecimal longitude;

    private String address;
}