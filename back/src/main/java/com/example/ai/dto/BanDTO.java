package com.example.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class BanDTO {

    @NotNull(message = "用户ID不能为空")
    private Long userId;

    private String reason;
}