package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.entity.Course;
import com.example.ai.entity.CourseStudent;
import com.example.ai.mapper.CourseMapper;
import com.example.ai.mapper.CourseStudentMapper;
import com.example.ai.service.CourseAccessService;
import com.example.ai.security.Roles;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseAccessServiceImpl implements CourseAccessService {

    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;

    @Override
    public Course requireCourse(Long courseId) {
        if (courseId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课程ID不能为空");
        }
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程不存在");
        }
        return course;
    }

    @Override
    public Course requireTeacher(Long courseId, Long userId, String role) {
        Course course = requireCourse(courseId);
        if (!Roles.TEACHER.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有教师才能执行该操作");
        }
        if (!course.getTeacherId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己创建的课程");
        }
        return course;
    }

    @Override
    public Course requireMember(Long courseId, Long userId, String role) {
        Course course = requireCourse(courseId);
        if (Roles.TEACHER.equals(role) && course.getTeacherId().equals(userId)) {
            return course;
        }
        if (Roles.STUDENT.equals(role)) {
            CourseStudent cs = courseStudentMapper.selectOne(
                    new LambdaQueryWrapper<CourseStudent>()
                            .eq(CourseStudent::getCourseId, courseId)
                            .eq(CourseStudent::getStudentId, userId)
                            .eq(CourseStudent::getStatus, 1)
            );
            if (cs != null) {
                return course;
            }
            throw new BizException(ErrorCode.FORBIDDEN, "您未加入该课程，无权访问");
        }
        throw new BizException(ErrorCode.FORBIDDEN, "无权访问该课程");
    }

    @Override
    public void requireStudent(String role) {
        if (!Roles.STUDENT.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有学生才能执行该操作");
        }
    }
}