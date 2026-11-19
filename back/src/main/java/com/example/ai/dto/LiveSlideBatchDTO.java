package com.example.ai.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class LiveSlideBatchDTO {

    @NotNull(message = "课堂ID不能为空")
    private Long sessionId;

    @NotEmpty(message = "课件页地址不能为空")
    private List<String> pageUrls;

    private String fileName;

    private String documentUrl;
}