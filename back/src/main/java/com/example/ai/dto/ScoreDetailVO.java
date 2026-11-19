package com.example.ai.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ScoreDetailVO {

    private Long courseId;

    private String courseName;

    private Long studentId;

    private String studentName;

    private String studentNo;

    private Integer checkinWeight;

    private Integer homeworkWeight;

    private Integer quizWeight;

    private Integer checkinCount;

    private Integer totalCheckin;

    private BigDecimal checkinScore;

    private BigDecimal homeworkScore;

    private Integer quizCount;

    private Integer totalQuiz;

    private BigDecimal quizScore;

    private BigDecimal totalScore;

    private List<CheckinItem> checkins;

    private List<HomeworkItem> homeworks;

    private List<QuizItem> quizzes;

    @Data
    public static class CheckinItem {
        private Long checkinId;
        private String title;
        private LocalDateTime checkinTime;
        private LocalDateTime submitTime;
        private Integer status;
        private String address;
    }

    @Data
    public static class HomeworkItem {
        private Long homeworkId;
        private String title;
        private BigDecimal fullScore;
        private BigDecimal score;
        private BigDecimal normalizedScore;
        private Integer status;
        private Integer isLate;
        private String comment;
        private LocalDateTime submitTime;
    }

    @Data
    public static class QuizItem {
        private Long assignmentId;
        private String title;
        private BigDecimal fullScore;
        private BigDecimal score;
        private BigDecimal normalizedScore;
        private Integer status;
        private LocalDateTime submitTime;
    }
}