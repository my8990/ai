package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class LiveSlideAddDTO {

    @NotNull(message = "课堂ID不能为空")
    private Long sessionId;

    @NotBlank(message = "课件图片地址不能为空")
    private String imageUrl;

    private String fileName;

    private String documentUrl;
}