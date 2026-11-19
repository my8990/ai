package com.example.ai.service;

import com.example.ai.dto.*;

import com.example.ai.entity.ScoreRule;

import java.util.List;

public interface ScoreRuleService {

    ScoreRule setRule(ScoreRuleDTO dto, Long userId, String role);

    ScoreRule getRule(Long courseId, Long userId, String role);

    List<StudentScoreVO> getCourseScores(Long courseId, Long userId, String role);

    MyScoreVO getMyScore(Long courseId, Long userId, String role);

    ScoreDetailVO getScoreDetail(Long courseId, Long studentId, Long userId, String role);

    String getCourseName(Long courseId);

    List<StudentScoreVO> getCourseScoresSortedByStudentNo(Long courseId, Long userId, String role);

    List<ScoreDetailExportVO> getExportDetailRows(Long courseId, Long userId, String role);
}