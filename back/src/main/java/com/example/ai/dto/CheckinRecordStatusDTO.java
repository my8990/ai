package com.example.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckinRecordStatusDTO {

    @NotNull(message = "签到活动ID不能为空")
    private Long checkinId;

    @NotNull(message = "学生ID不能为空")
    private Long studentId;

    @NotNull(message = "签到状态不能为空")
    @Min(value = 1, message = "签到状态取值为 1出勤 2迟到 3请假 4缺勤")
    @Max(value = 4, message = "签到状态取值为 1出勤 2迟到 3请假 4缺勤")
    private Integer status;
}