package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("quiz")
public class Quiz {

    public static final int STATUS_DRAFT = 0;

    public static final int STATUS_READY = 1;

    public static final int STATUS_PUBLISHED = STATUS_READY;

    public static final int STATUS_ENDED = 2;

    public static final int VISIBILITY_PRIVATE = 0;

    public static final int VISIBILITY_PUBLIC = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private String title;

    private String description;

    private BigDecimal fullScore;

    private Integer durationMinutes;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer showAnswerAfter;

    private Long teacherId;

    private Long sourceQuizId;

    private Integer status;

    private Integer visibility;

    private Integer favoriteCount;

    private Integer useCount;

    private LocalDateTime sharedTime;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}