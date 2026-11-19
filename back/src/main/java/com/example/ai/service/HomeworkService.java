package com.example.ai.service;

import com.example.ai.dto.*;

import com.example.ai.entity.Homework;

import java.util.List;

public interface HomeworkService {

    Homework createHomework(HomeworkCreateDTO dto, Long userId, String role);

    Homework updateHomework(HomeworkUpdateDTO dto, Long userId, String role);

    void deleteHomework(Long homeworkId, Long userId, String role);

    List<HomeworkVO> getCourseHomework(Long courseId, Long userId, String role);

    HomeworkVO getHomeworkDetail(Long homeworkId, Long userId, String role);

    void submitHomework(HomeworkSubmitDTO dto, Long userId, String role);

    void gradeHomework(HomeworkGradeDTO dto, Long userId, String role);

    List<HomeworkSubmitVO> getHomeworkSubmissions(Long homeworkId, Long userId, String role);

    HomeworkSubmitVO getMySubmit(Long homeworkId, Long userId, String role);

    List<HomeworkVO> getMyHomework(Long courseId, Long userId, String role);

    List<HomeworkVO> getMyAllHomework(Long userId, String role);
}