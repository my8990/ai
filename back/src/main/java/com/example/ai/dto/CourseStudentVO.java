package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CourseStudentVO {

    private Long id;
    private Long studentId;
    private String username;
    private String nickname;
    private String studentNo;
    private Integer status;
    private LocalDateTime createTime;
}