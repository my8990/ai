package com.example.ai.service;

import com.example.ai.dto.*;

import java.util.List;

public interface QuizService {

    QuizVO createQuiz(QuizCreateDTO dto, Long userId, String role);

    QuizVO updateQuiz(QuizCreateDTO dto, Long userId, String role);

    void publishQuiz(Long quizId, Long userId, String role);

    void deleteQuiz(Long quizId, Long userId, String role);

    List<QuizVO> getCourseQuizzes(Long courseId, Long userId, String role);

    QuizVO getQuizDetail(Long quizId, Long userId, String role);

    QuizTakeVO takeQuiz(Long quizId, Long assignmentId, Long userId, String role);

    QuizRecordVO submitQuiz(QuizSubmitDTO dto, Long userId, String role);

    QuizRecordVO getMyRecord(Long quizId, Long assignmentId, Long userId, String role);

    List<QuizVO> getMyAllQuizzes(Long userId, String role);

    List<QuizVO> getMyQuizzes(Long userId, String role);

    void shareQuiz(Long quizId, boolean share, Long userId, String role);

    List<QuizVO> getMarketQuizzes(String keyword, String sort, Long userId, String role);

    void favoriteQuiz(Long quizId, Long userId, String role);

    void unfavoriteQuiz(Long quizId, Long userId, String role);

    List<QuizVO> getMyFavorites(Long userId, String role);

    QuizVO forkQuiz(Long quizId, Long userId, String role);

    QuizVO assignQuiz(QuizAssignDTO dto, Long userId, String role);

    QuizVO updateAssignment(QuizAssignDTO dto, Long userId, String role);

    void publishAssignment(Long assignmentId, Long userId, String role);

    void endAssignment(Long assignmentId, Long userId, String role);

    void deleteAssignment(Long assignmentId, Long userId, String role);
}