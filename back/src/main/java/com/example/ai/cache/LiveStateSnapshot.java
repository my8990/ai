package com.example.ai.cache;

import com.example.ai.dto.LiveQuizVO;
import com.example.ai.dto.LiveSlideVO;
import lombok.Data;

@Data
public class LiveStateSnapshot {

    private int currentPage;

    private int slideTotal;

    private Long checkinId;

    private int checkedCount;

    private int totalCount;

    private int onlineCount;

    private int handCount;

    private int pendingQuestionCount;

    private LiveSlideVO currentSlide;

    private LiveQuizVO currentQuiz;

    private long updatedAt;
}