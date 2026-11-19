package com.example.ai.service;

import com.example.ai.dto.LiveInteractionVO;
import com.example.ai.dto.LiveJoinDTO;
import com.example.ai.dto.LivePageDTO;
import com.example.ai.dto.LiveQuestionDTO;
import com.example.ai.dto.LiveQuizPublishDTO;
import com.example.ai.dto.LiveQuizVO;
import com.example.ai.dto.LiveReportVO;
import com.example.ai.dto.LiveSessionVO;
import com.example.ai.dto.LiveSlideAddDTO;
import com.example.ai.dto.LiveSlideBatchDTO;
import com.example.ai.dto.LiveSlideVO;
import com.example.ai.dto.LiveStartDTO;
import com.example.ai.dto.LiveStateVO;
import com.example.ai.dto.LiveStudentVO;
import com.example.ai.dto.LiveWsTicketVO;

import java.util.List;
import java.util.Set;

public interface LiveService {

    LiveSessionVO start(LiveStartDTO dto, Long userId, String role);

    void end(Long sessionId, Long userId, String role);

    void end(Long sessionId, Long userId, String role, boolean publishCourseware);

    LiveStateVO join(LiveJoinDTO dto, Long userId, String role);

    LiveStateVO getState(Long sessionId, Long userId, String role);

    LiveStateVO turnPage(LivePageDTO dto, Long userId, String role);

    LiveQuizVO publishQuiz(Long sessionId, LiveQuizPublishDTO dto, Long userId, String role);

    void endQuiz(Long sessionId, Long userId, String role);

    List<LiveSlideVO> getSlides(Long sessionId, Long userId, String role);

    LiveSlideVO addSlide(LiveSlideAddDTO dto, Long userId, String role);

    List<LiveSlideVO> addSlides(LiveSlideBatchDTO dto, Long userId, String role);

    Long deleteSlide(Long slideId, Long userId, String role);

    LiveSessionVO current(Long courseId, Long userId, String role);

    List<LiveSessionVO> listActive(Long userId, String role);

    String getWxacode(Long sessionId, Long userId, String role);

    void heartbeat(Long sessionId, Long userId, String role);

    List<LiveStudentVO> onlineStudents(Long sessionId, Long userId, String role);

    List<LiveSessionVO> history(Long courseId, Long userId, String role);

    LiveReportVO report(Long sessionId, Long userId, String role);

    LiveStateVO getPushState(Long sessionId);

    LiveStateVO refreshPushState(Long sessionId);

    void evictStateCache(Long sessionId);

    boolean toggleHand(Long sessionId, Long userId, String role);

    Long askQuestion(LiveQuestionDTO dto, Long userId, String role);

    List<LiveInteractionVO> interactions(Long sessionId, Long userId, String role);

    Long resolveInteraction(Long id, Long userId, String role);

    LiveWsTicketVO createWsTicket(Long sessionId, Long userId, String role);

    Set<Long> pendingHandUserIds(Long sessionId);

    WsPrincipal consumeWsTicket(String ticket);

    record WsPrincipal(Long sessionId, Long userId, String role) {
    }
}