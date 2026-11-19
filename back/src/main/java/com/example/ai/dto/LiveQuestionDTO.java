package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LiveQuestionDTO {

    @NotNull(message = "课堂ID不能为空")
    private Long sessionId;

    @NotBlank(message = "提问内容不能为空")
    @Size(max = 200, message = "提问内容不能超过200字")
    private String content;
}