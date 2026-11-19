package com.example.ai.service;

import com.example.ai.dto.*;

import java.util.List;

public interface CourseService {

    CourseVO createCourse(CourseCreateDTO dto, Long userId, String role);

    CourseVO updateCourse(CourseUpdateDTO dto, Long userId, String role);

    CourseVO getCourseDetail(Long courseId, Long userId, String role);

    List<CourseVO> getMyCourses(Long userId, String role);

    void toggleCourseStatus(Long courseId, Long userId, String role);

    void dissolveCourse(Long courseId, Long userId, String role);

    List<CourseStudentVO> getStudentList(Long courseId, Long userId, String role);

    Integer getStudentCount(Long courseId, Long userId, String role);

    void joinCourse(String code, Long userId, String role);

    void quitCourse(Long courseId, Long userId);

    void removeStudent(Long courseId, Long studentId, Long userId, String role);
}