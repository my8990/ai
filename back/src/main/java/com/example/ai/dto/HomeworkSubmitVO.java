package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HomeworkSubmitVO {

    private Long id;

    private Long homeworkId;

    private Long studentId;

    private String studentName;

    private String studentNo;

    private String content;

    private List<String> imageList;

    private String fileUrl;

    private String fileName;

    private BigDecimal score;

    private String comment;

    private Integer status;

    private Integer isLate;

    private LocalDateTime createTime;
}