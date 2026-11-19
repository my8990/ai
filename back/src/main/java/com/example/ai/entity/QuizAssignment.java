package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quiz_assignment")
public class QuizAssignment {

    public static final int STATUS_PENDING = 0;

    public static final int STATUS_OPEN = 1;

    public static final int STATUS_ENDED = 2;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long quizId;

    private Long courseId;

    private Long teacherId;

    private String title;

    private Integer durationMinutes;

    private LocalDateTime startTime;

    private LocalDateTime endTime;

    private Integer status;

    @TableLogic
    private Integer deleted;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}