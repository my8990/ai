package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseVO {

    private Long id;
    private String name;
    private String semester;
    private String college;
    private String description;
    private String cover;
    private String code;
    private Integer status;
    private Long teacherId;
    private String teacherName;
    private Integer studentCount;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}