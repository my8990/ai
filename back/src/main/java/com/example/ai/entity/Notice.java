package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("notice")
public class Notice {

    public static final int TYPE_COURSEWARE = 1;

    public static final int TYPE_TEACHER = 2;

    public static final int TYPE_DISCUSSION = 3;

    public static final int TYPE_QUIZ = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Integer type;

    private String title;

    private String content;

    private Long sessionId;

    private Long materialId;

    private Long teacherId;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}