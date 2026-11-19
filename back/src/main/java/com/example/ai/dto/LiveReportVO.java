package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class LiveReportVO {

    private Long sessionId;
    private Long courseId;
    private String courseName;
    private String title;
    private String teacherName;
    private String joinCode;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Long durationMinutes;
    private Integer slideTotal;
    private Integer totalCount;
    private Integer checkedCount;
    private Integer lateCount;
    private Integer absentCount;
    private Integer onlineCount;
    private List<LiveStudentVO> students;
}