package com.example.ai.service;

import com.example.ai.entity.Course;

public interface CourseAccessService {

    Course requireCourse(Long courseId);

    Course requireTeacher(Long courseId, Long userId, String role);

    Course requireMember(Long courseId, Long userId, String role);

    void requireStudent(String role);
}