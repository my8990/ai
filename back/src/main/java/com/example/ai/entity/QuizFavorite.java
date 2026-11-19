package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("quiz_favorite")
public class QuizFavorite {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long teacherId;

    private Long quizId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}