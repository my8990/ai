package com.example.ai.service;

import com.example.ai.dto.LiveEndPreviewVO;

public interface CoursewarePublishService {

    LiveEndPreviewVO preview(Long sessionId, Long userId, String role);

    Long publish(Long sessionId, Long userId, String role);

    void publishAsync(Long sessionId, Long userId, String role);
}