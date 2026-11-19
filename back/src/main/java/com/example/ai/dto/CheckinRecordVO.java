package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class CheckinRecordVO {

    private Long id;
    private Long studentId;
    private String username;
    private String nickname;
    private String studentNo;
    private Boolean checked;
    private Integer status;
    private LocalDateTime checkinTime;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String address;
}