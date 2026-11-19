package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class AppealDTO {

    @NotBlank(message = "申诉理由不能为空")
    private String reason;
}