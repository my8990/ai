package com.example.ai.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CourseUpdateDTO {

    @NotNull(message = "课程ID不能为空")
    private Long id;

    @Size(max = 100, message = "课程名称最长100字")
    private String name;

    @Size(max = 50, message = "学期最长50字")
    private String semester;

    @Size(max = 100, message = "学院最长100字")
    private String college;

    private String description;

    private String cover;
}