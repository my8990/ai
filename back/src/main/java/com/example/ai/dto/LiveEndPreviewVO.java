package com.example.ai.dto;

import lombok.Data;

@Data
public class LiveEndPreviewVO {

    private Long sessionId;

    private Long courseId;

    private String courseName;

    private String title;

    private String sessionDate;

    private Boolean hasCourseware;

    private Integer slideTotal;

    private Boolean published;

    private Long materialId;

    private String publishFileName;

    private String publishType;
}