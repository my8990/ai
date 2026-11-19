package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.NoticeCreateDTO;
import com.example.ai.dto.NoticeVO;
import com.example.ai.entity.Course;
import com.example.ai.entity.CourseStudent;
import com.example.ai.entity.Notice;
import com.example.ai.mapper.CourseMapper;
import com.example.ai.mapper.CourseStudentMapper;
import com.example.ai.mapper.NoticeMapper;
import com.example.ai.service.CourseAccessService;
import com.example.ai.security.Roles;
import com.example.ai.service.NoticeService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NoticeServiceImpl implements NoticeService {

    private static final int MAX_NOTICES = 30;

    private final NoticeMapper noticeMapper;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final CourseAccessService courseAccessService;

    @Override
    public List<NoticeVO> listByCourse(Long courseId, Long userId, String role) {
        Course course = courseAccessService.requireMember(courseId, userId, role);
        LambdaQueryWrapper<Notice> wrapper = new LambdaQueryWrapper<Notice>()
                .eq(Notice::getCourseId, courseId);
        if (Roles.TEACHER.equals(role)) {
            wrapper.eq(Notice::getType, 2);
        }
        List<Notice> list = noticeMapper.selectList(
                wrapper
                        .orderByDesc(Notice::getCreateTime)
                        .orderByDesc(Notice::getId)
                        .last("limit " + MAX_NOTICES)
        );
        List<NoticeVO> result = new ArrayList<>();
        for (Notice notice : list) {
            result.add(toVO(notice, course));
        }
        return result;
    }

    @Override
    public List<NoticeVO> listMyNotices(Long userId, String role) {
        List<Long> courseIds;
        if (Roles.TEACHER.equals(role)) {
            List<Course> teacherCourses = courseMapper.selectList(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, userId)
                            .eq(Course::getStatus, 1)
            );
            courseIds = teacherCourses.stream().map(Course::getId).collect(Collectors.toList());
        } else if (Roles.STUDENT.equals(role)) {
            List<CourseStudent> relations = courseStudentMapper.selectList(
                    new LambdaQueryWrapper<CourseStudent>()
                            .eq(CourseStudent::getStudentId, userId)
                            .eq(CourseStudent::getStatus, 1)
            );
            if (relations.isEmpty()) {
                return Collections.emptyList();
            }
            List<Long> rawIds = relations.stream().map(CourseStudent::getCourseId).collect(Collectors.toList());
            List<Course> validCourses = courseMapper.selectBatchIds(rawIds);
            courseIds = validCourses.stream()
                    .filter(c -> c.getStatus() == 1)
                    .map(Course::getId)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }

        if (courseIds.isEmpty()) {
            return Collections.emptyList();
        }

        List<Notice> list = noticeMapper.selectList(
                new LambdaQueryWrapper<Notice>()
                        .in(Notice::getCourseId, courseIds)
                        .eq(Roles.TEACHER.equals(role), Notice::getType, 2)
                        .orderByDesc(Notice::getCreateTime)
                        .orderByDesc(Notice::getId)
                        .last("limit " + MAX_NOTICES)
        );

        if (list.isEmpty()) {
            return Collections.emptyList();
        }

        List<Long> neededCourseIds = list.stream()
                .map(Notice::getCourseId)
                .distinct()
                .collect(Collectors.toList());
        List<Course> courses = courseMapper.selectBatchIds(neededCourseIds);
        Map<Long, Course> courseMap = courses.stream()
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        List<NoticeVO> result = new ArrayList<>();
        for (Notice notice : list) {
            Course course = courseMap.get(notice.getCourseId());
            result.add(toVO(notice, course));
        }
        return result;
    }

    @Override
    public NoticeVO createTeacherNotice(NoticeCreateDTO dto, Long userId, String role) {
        Course course = courseAccessService.requireTeacher(dto.getCourseId(), userId, role);
        Notice notice = new Notice();
        notice.setCourseId(dto.getCourseId());
        notice.setType(Notice.TYPE_TEACHER);
        notice.setTitle(dto.getTitle());
        notice.setContent(dto.getContent());
        notice.setTeacherId(userId);
        noticeMapper.insert(notice);
        return toVO(notice, course);
    }

    @Override
    public void deleteNotice(Long noticeId, Long userId, String role) {
        if (!Roles.TEACHER.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有教师才能删除公告");
        }
        Notice notice = noticeMapper.selectById(noticeId);
        if (notice == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "公告不存在");
        }
        if (notice.getType() != null && (notice.getType() == 1 || notice.getType() == 3)) {
            throw new BizException(ErrorCode.FORBIDDEN, "系统自动公告不可删除");
        }
        noticeMapper.deleteById(noticeId);
    }

    private NoticeVO toVO(Notice notice, Course course) {
        NoticeVO vo = new NoticeVO();
        vo.setId(notice.getId());
        vo.setCourseId(notice.getCourseId());
        vo.setCourseName(course == null ? null : course.getName());
        vo.setType(notice.getType());
        vo.setTitle(notice.getTitle());
        vo.setContent(notice.getContent());
        vo.setSessionId(notice.getSessionId());
        vo.setMaterialId(notice.getMaterialId());
        vo.setTeacherId(notice.getTeacherId());
        vo.setTeacherName(course == null ? null : course.getTeacherName());
        vo.setCreateTime(notice.getCreateTime());
        return vo;
    }
}