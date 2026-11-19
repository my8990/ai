package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("score_rule")
public class ScoreRule {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long courseId;

    private Integer checkinWeight;

    private Integer homeworkWeight;

    private Integer quizWeight;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}