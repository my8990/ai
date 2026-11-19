package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_broadcast_event")
public class LiveBroadcastEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long sessionId;

    private Integer forceRefresh;

    private String instanceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}