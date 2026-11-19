package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("live_ws_ticket")
public class LiveWsTicket {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String ticket;

    private Long sessionId;

    private Long userId;

    private String role;

    private LocalDateTime expireTime;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}