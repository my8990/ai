package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class JoinCourseDTO {

    @NotBlank(message = "课程码不能为空")
    private String code;
}