package com.example.ai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class MaterialCreateDTO {

    @NotNull(message = "课程ID不能为空")
    private Long courseId;

    @NotBlank(message = "课件标题不能为空")
    @Size(max = 100, message = "课件标题不能超过100字")
    private String title;

    @Size(max = 500, message = "课件说明不能超过500字")
    private String description;

    @NotBlank(message = "请先上传课件文件")
    private String fileUrl;

    private String fileName;

    private Long fileSize;

    private String fileType;
}