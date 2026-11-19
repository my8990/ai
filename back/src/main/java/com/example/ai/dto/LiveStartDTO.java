package com.example.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LiveStartDTO {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    private String title;

    private Integer expireMinutes;

    private Integer lateMinutes;
}