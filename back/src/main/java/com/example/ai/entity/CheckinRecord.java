package com.example.ai.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("checkin_record")
public class CheckinRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long checkinId;

    private Long studentId;

    private Integer status;

    private java.math.BigDecimal latitude;

    private java.math.BigDecimal longitude;

    private String address;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}