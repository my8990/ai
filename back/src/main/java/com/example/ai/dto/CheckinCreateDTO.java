package com.example.ai.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CheckinCreateDTO {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @NotBlank(message = "签到标题不能为空")
    private String title;

    @NotNull(message = "签到类型不能为空")
    @Min(value = 1, message = "签到类型取值为 1数字签到 2二维码签到 3扫码或数字")
    @Max(value = 3, message = "签到类型取值为 1数字签到 2二维码签到 3扫码或数字")
    private Integer type;

    private Integer expireMinutes;

    private Integer lateMinutes;
}