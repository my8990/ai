package com.example.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
@Schema(description = "审计日志查询条件")
public class AuditLogQueryDTO {

    @Schema(description = "页码，从1开始", defaultValue = "1")
    @Min(value = 1, message = "页码不能小于1")
    private Long pageNum = 1L;

    @Schema(description = "每页条数，最大100", defaultValue = "20")
    @Min(value = 1, message = "每页条数不能小于1")
    @Max(value = 100, message = "每页条数不能超过100")
    private Long pageSize = 20L;

    @Schema(description = "操作人ID")
    private Long userId;

    @Schema(description = "业务模块，如：用户/课程/作业")
    private String module;

    @Schema(description = "操作动作，如：禁用账号/删除作业")
    private String action;

    @Schema(description = "是否成功: 1成功 0失败")
    @Min(value = 0, message = "success取值只能是0或1")
    @Max(value = 1, message = "success取值只能是0或1")
    private Integer success;

    @Schema(description = "开始日期(含)，格式yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate startDate;

    @Schema(description = "结束日期(含)，格式yyyy-MM-dd")
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate endDate;
}