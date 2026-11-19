package com.example.ai.service;

import com.example.ai.dto.CheckinCreateDTO;
import com.example.ai.dto.CheckinRecordStatusDTO;
import com.example.ai.dto.CheckinSubmitByCodeDTO;
import com.example.ai.dto.CheckinSubmitDTO;
import com.example.ai.dto.CheckinVO;

import java.util.List;

public interface CheckinService {

    CheckinVO createCheckin(CheckinCreateDTO dto, Long userId, String role);

    void endCheckin(Long checkinId, Long userId, String role);

    void deleteCheckin(Long checkinId, Long userId, String role);

    List<CheckinVO> getCourseCheckins(Long courseId, Long userId, String role);

    CheckinVO getCheckinDetail(Long checkinId, Long userId, String role);

    void submitCheckin(CheckinSubmitDTO dto, Long userId, String role);

    Long submitCheckinByCode(CheckinSubmitByCodeDTO dto, Long userId, String role);

    CheckinVO updateRecordStatus(CheckinRecordStatusDTO dto, Long userId, String role);

    List<CheckinVO> getMyCheckins(Long userId, String role);
}