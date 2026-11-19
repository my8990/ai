package com.example.ai.service;

import com.example.ai.dto.CoursewareUploadVO;
import org.springframework.web.multipart.MultipartFile;

public interface CoursewareConvertService {

    CoursewareUploadVO convert(MultipartFile file);
}