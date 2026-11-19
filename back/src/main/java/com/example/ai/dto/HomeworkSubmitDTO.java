package com.example.ai.dto;

import lombok.Data;

@Data
public class HomeworkSubmitDTO {

    private Long homeworkId;

    private String content;

    private String images;

    private String fileUrl;

    private String fileName;
}