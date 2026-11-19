package com.example.ai.service;

import com.example.ai.dto.NoticeCreateDTO;
import com.example.ai.dto.NoticeVO;

import java.util.List;

public interface NoticeService {

    List<NoticeVO> listByCourse(Long courseId, Long userId, String role);

    List<NoticeVO> listMyNotices(Long userId, String role);

    NoticeVO createTeacherNotice(NoticeCreateDTO dto, Long userId, String role);

    void deleteNotice(Long noticeId, Long userId, String role);
}