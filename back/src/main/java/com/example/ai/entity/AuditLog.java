package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("audit_log")
public class AuditLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long userId;

    private String username;

    private String role;

    private String module;

    private String action;

    private String method;

    private String uri;

    private Integer httpStatus;

    private Integer success;

    private String errorMessage;

    private String ip;

    private Long durationMs;

    private String traceId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}