package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class CheckinVO {

    private Long id;
    private Long courseId;
    private String courseName;
    private String title;
    private Integer type;
    private Integer lateMinutes;
    private String code;
    private String qrToken;
    private Integer status;
    private Long teacherId;
    private LocalDateTime expireTime;
    private Integer checkedCount;
    private Integer lateCount;
    private Integer totalCount;
    private Boolean checked;
    private Integer myStatus;
    private LocalDateTime createTime;

    private LocalDateTime myCheckinTime;
    private BigDecimal myLatitude;
    private BigDecimal myLongitude;
    private String myAddress;

    private List<CheckinRecordVO> records;
}