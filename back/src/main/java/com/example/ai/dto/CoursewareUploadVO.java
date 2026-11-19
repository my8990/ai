package com.example.ai.dto;

import lombok.Data;

import java.util.List;

@Data
public class CoursewareUploadVO {

    private String fileName;

    private String documentUrl;

    private List<String> pageUrls;

    private Integer pageCount;

    private Boolean truncated;
}