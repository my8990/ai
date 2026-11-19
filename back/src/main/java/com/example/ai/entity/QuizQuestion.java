package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("quiz_question")
public class QuizQuestion {

    public static final int TYPE_SINGLE = 1;

    public static final int TYPE_MULTI = 2;

    public static final int TYPE_JUDGE = 3;

    public static final int TYPE_BLANK = 4;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long quizId;

    private Integer type;

    private String content;

    private String options;

    private String correctAnswer;

    private BigDecimal score;

    private String analysis;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}