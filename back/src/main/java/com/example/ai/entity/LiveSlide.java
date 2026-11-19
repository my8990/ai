package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_slide")
public class LiveSlide {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Integer pageNo;

    private String imageUrl;

    private String fileName;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}