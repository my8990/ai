package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_session")
public class LiveSession {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Long teacherId;

    private Long checkinId;

    private Long quizAssignmentId;

    private String joinCode;

    private String title;

    private Integer currentPage;

    private Integer slideTotal;

    private Integer status;

    private LocalDateTime endTime;

    private Integer enterCount;

    private String originalDocUrl;

    private String originalFileName;

    private Integer coursewareStatus;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}