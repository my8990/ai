package com.example.ai.dto;

import lombok.Data;

@Data
public class LiveStateVO {

    private Long sessionId;
    private Long courseId;
    private String courseName;
    private String title;
    private String joinCode;
    private Integer status;
    private Integer currentPage;
    private Integer slideTotal;
    private LiveSlideVO currentSlide;
    private Long checkinId;
    private Integer checkedCount;
    private Integer totalCount;
    private Integer onlineCount;
    private Integer handCount;
    private Integer pendingQuestionCount;
    private Boolean myHandRaised;
    private LiveQuizVO currentQuiz;
}