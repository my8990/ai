package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_interaction")
public class LiveInteraction {

    public static final String TYPE_HAND = "hand";
    public static final String TYPE_QUESTION = "question";

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RESOLVED = 1;

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Long userId;

    private String type;

    private String content;

    private Integer status;

    private LocalDateTime handleTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}