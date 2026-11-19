package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MaterialVO {

    private Long id;

    private Long courseId;

    private String title;

    private String description;

    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private String fileType;

    private Integer status;

    private Long teacherId;

    private String teacherName;

    private Long liveSessionId;

    private Integer source;

    private Integer pageCount;

    private LocalDateTime createTime;
}