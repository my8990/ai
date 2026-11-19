package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("quiz_record")
public class QuizRecord {

    public static final int STATUS_DOING = 0;

    public static final int STATUS_SUBMITTED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long quizId;

    private Long assignmentId;

    private Long studentId;

    private BigDecimal score;

    private Integer status;

    private LocalDateTime startAt;

    private LocalDateTime submitAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}