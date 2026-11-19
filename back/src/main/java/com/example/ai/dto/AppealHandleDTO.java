package com.example.ai.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AppealHandleDTO {

    @NotNull(message = "申诉ID不能为空")
    private Long appealId;

    @NotNull(message = "处理结果不能为空")
    private Integer status;

    private String reply;
}