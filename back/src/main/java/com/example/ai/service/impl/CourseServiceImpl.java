package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.*;
import com.example.ai.entity.Checkin;
import com.example.ai.entity.CheckinRecord;
import com.example.ai.entity.Course;
import com.example.ai.entity.CourseStudent;
import com.example.ai.entity.Homework;
import com.example.ai.entity.HomeworkSubmit;
import com.example.ai.entity.ScoreRule;
import com.example.ai.entity.User;
import com.example.ai.mapper.CheckinMapper;
import com.example.ai.mapper.CheckinRecordMapper;
import com.example.ai.mapper.CourseMapper;
import com.example.ai.mapper.CourseStudentMapper;
import com.example.ai.mapper.HomeworkMapper;
import com.example.ai.mapper.HomeworkSubmitMapper;
import com.example.ai.mapper.ScoreRuleMapper;
import com.example.ai.mapper.UserMapper;
import com.example.ai.security.FileUrlSigner;
import com.example.ai.security.Roles;
import com.example.ai.service.CourseAccessService;
import com.example.ai.service.CourseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CourseServiceImpl implements CourseService {

    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final UserMapper userMapper;
    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmitMapper homeworkSubmitMapper;
    private final CheckinMapper checkinMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final ScoreRuleMapper scoreRuleMapper;
    private final CourseAccessService courseAccessService;
    private final FileUrlSigner fileUrlSigner;

    private static final String CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int CODE_LENGTH = 6;
    private static final int CODE_MAX_ATTEMPTS = 5;
    private static final SecureRandom CODE_RANDOM = new SecureRandom();

    @Override
    public CourseVO createCourse(CourseCreateDTO dto, Long userId, String role) {
        if (!Roles.TEACHER.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有教师才能创建课程");
        }

        User teacher = userMapper.selectById(userId);
        if (teacher == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "教师信息不存在");
        }

        Course course = new Course();
        course.setName(dto.getName());
        course.setSemester(dto.getSemester());
        course.setCollege(dto.getCollege());
        course.setDescription(dto.getDescription());
        course.setCover(fileUrlSigner.normalize(dto.getCover()));
        course.setStatus(1);
        course.setTeacherId(userId);
        course.setTeacherName(teacher.getNickname() != null ? teacher.getNickname() : teacher.getUsername());

        insertWithUniqueCode(course);

        return toVO(course, 0);
    }

    @Override
    public CourseVO updateCourse(CourseUpdateDTO dto, Long userId, String role) {
        Course course = courseAccessService.requireTeacher(dto.getId(), userId, role);

        if (dto.getName() != null) course.setName(dto.getName());
        if (dto.getSemester() != null) course.setSemester(dto.getSemester());
        if (dto.getCollege() != null) course.setCollege(dto.getCollege());
        if (dto.getDescription() != null) course.setDescription(dto.getDescription());
        if (dto.getCover() != null) course.setCover(fileUrlSigner.normalize(dto.getCover()));

        courseMapper.updateById(course);

        int count = getActiveStudentCount(course.getId());
        return toVO(course, count);
    }

    @Override
    public CourseVO getCourseDetail(Long courseId, Long userId, String role) {
        Course course = courseAccessService.requireMember(courseId, userId, role);

        int count = getActiveStudentCount(courseId);
        return toVO(course, count);
    }

    @Override
    public List<CourseVO> getMyCourses(Long userId, String role) {
        List<CourseVO> result = new ArrayList<>();

        if (Roles.TEACHER.equals(role)) {
            List<Course> courses = courseMapper.selectList(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, userId)
                            .orderByDesc(Course::getCreateTime)
            );
            java.util.Map<Long, Integer> countMap = batchStudentCount(courses.stream().map(Course::getId).toList());
            for (Course c : courses) {
                result.add(toVO(c, countMap.getOrDefault(c.getId(), 0)));
            }
        } else if (Roles.STUDENT.equals(role)) {
            List<CourseStudent> csList = courseStudentMapper.selectList(
                    new LambdaQueryWrapper<CourseStudent>()
                            .eq(CourseStudent::getStudentId, userId)
                            .eq(CourseStudent::getStatus, 1)
                            .orderByDesc(CourseStudent::getCreateTime)
            );
            if (!csList.isEmpty()) {
                List<Long> courseIds = csList.stream().map(CourseStudent::getCourseId).distinct().toList();
                List<Course> courseList = courseMapper.selectBatchIds(courseIds);
                java.util.Map<Long, Course> courseMap = new java.util.HashMap<>();
                for (Course c : courseList) {
                    courseMap.put(c.getId(), c);
                }
                java.util.Map<Long, Integer> countMap = batchStudentCount(courseIds);
                for (CourseStudent cs : csList) {
                    Course c = courseMap.get(cs.getCourseId());
                    if (c != null) {
                        result.add(toVO(c, countMap.getOrDefault(c.getId(), 0)));
                    }
                }
            }
        }

        return result;
    }

    @Override
    public void toggleCourseStatus(Long courseId, Long userId, String role) {
        Course course = courseAccessService.requireTeacher(courseId, userId, role);

        course.setStatus(course.getStatus() == 1 ? 0 : 1);
        courseMapper.updateById(course);
    }

    @Override
    @Transactional
    public void dissolveCourse(Long courseId, Long userId, String role) {
        courseAccessService.requireTeacher(courseId, userId, role);

        List<Homework> homeworkList = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().eq(Homework::getCourseId, courseId)
        );
        List<Long> homeworkIds = homeworkList.stream().map(Homework::getId).toList();
        if (!homeworkIds.isEmpty()) {
            homeworkSubmitMapper.delete(
                    new LambdaQueryWrapper<HomeworkSubmit>().in(HomeworkSubmit::getHomeworkId, homeworkIds)
            );
            homeworkMapper.delete(
                    new LambdaQueryWrapper<Homework>().eq(Homework::getCourseId, courseId)
            );
        }

        List<Checkin> checkinList = checkinMapper.selectList(
                new LambdaQueryWrapper<Checkin>().eq(Checkin::getCourseId, courseId)
        );
        List<Long> checkinIds = checkinList.stream().map(Checkin::getId).toList();
        if (!checkinIds.isEmpty()) {
            checkinRecordMapper.delete(
                    new LambdaQueryWrapper<CheckinRecord>().in(CheckinRecord::getCheckinId, checkinIds)
            );
            checkinMapper.delete(
                    new LambdaQueryWrapper<Checkin>().eq(Checkin::getCourseId, courseId)
            );
        }

        scoreRuleMapper.delete(
                new LambdaQueryWrapper<ScoreRule>().eq(ScoreRule::getCourseId, courseId)
        );

        courseStudentMapper.delete(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
        );

        courseMapper.deleteById(courseId);
    }

    @Override
    public List<CourseStudentVO> getStudentList(Long courseId, Long userId, String role) {
        courseAccessService.requireTeacher(courseId, userId, role);

        List<CourseStudent> csList = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .orderByDesc(CourseStudent::getStatus)
                        .orderByAsc(CourseStudent::getCreateTime)
        );

        List<CourseStudentVO> result = new ArrayList<>();
        if (csList.isEmpty()) {
            return result;
        }
        List<Long> studentIds = csList.stream().map(CourseStudent::getStudentId).distinct().toList();
        List<User> studentList = userMapper.selectBatchIds(studentIds);
        java.util.Map<Long, User> studentMap = new java.util.HashMap<>();
        for (User u : studentList) {
            studentMap.put(u.getId(), u);
        }
        for (CourseStudent cs : csList) {
            User student = studentMap.get(cs.getStudentId());
            if (student != null) {
                CourseStudentVO vo = new CourseStudentVO();
                vo.setId(cs.getId());
                vo.setStudentId(student.getId());
                vo.setUsername(student.getUsername());
                vo.setNickname(student.getNickname());
                vo.setStudentNo(student.getStudentNo());
                vo.setStatus(cs.getStatus());
                vo.setCreateTime(cs.getCreateTime());
                result.add(vo);
            }
        }

        return result;
    }

    @Override
    public Integer getStudentCount(Long courseId, Long userId, String role) {
        courseAccessService.requireTeacher(courseId, userId, role);

        return getActiveStudentCount(courseId);
    }

    @Override
    public void joinCourse(String code, Long userId, String role) {
        if (!Roles.STUDENT.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有学生才能加入课程");
        }

        String upperCode = code.toUpperCase();

        Course course = courseMapper.selectOne(
                new LambdaQueryWrapper<Course>()
                        .eq(Course::getCode, upperCode)
        );
        if (course == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "课程码不存在");
        }

        if (course.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课程已关闭，无法加入");
        }

        CourseStudent existing = courseStudentMapper.selectOne(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, course.getId())
                        .eq(CourseStudent::getStudentId, userId)
        );
        if (existing != null && existing.getStatus() == 1) {
            throw new BizException(ErrorCode.CONFLICT, "您已加入该课程，不可重复加入");
        }

        if (existing != null && existing.getStatus() == 0) {
            existing.setStatus(1);
            courseStudentMapper.updateById(existing);
        } else {
            CourseStudent cs = new CourseStudent();
            cs.setCourseId(course.getId());
            cs.setStudentId(userId);
            cs.setStatus(1);
            try {
                courseStudentMapper.insert(cs);
            } catch (DuplicateKeyException e) {
                throw new BizException(ErrorCode.CONFLICT, "您已加入该课程，不可重复加入");
            }
        }
    }

    @Override
    public void quitCourse(Long courseId, Long userId) {
        CourseStudent cs = courseStudentMapper.selectOne(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStudentId, userId)
                        .eq(CourseStudent::getStatus, 1)
        );
        if (cs == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "您未加入该课程或已退课");
        }

        cs.setStatus(0);
        courseStudentMapper.updateById(cs);
    }

    private void insertWithUniqueCode(Course course) {
        for (int attempt = 1; attempt <= CODE_MAX_ATTEMPTS; attempt++) {
            course.setCode(generateCode());
            try {
                courseMapper.insert(course);
                return;
            } catch (DuplicateKeyException e) {
                course.setId(null);
                log.warn("课程加课码冲突，重新生成: attempt={}, code={}", attempt, course.getCode());
            }
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR, "生成课程码失败，请重试");
    }

    private String generateCode() {
        StringBuilder sb = new StringBuilder(CODE_LENGTH);
        for (int i = 0; i < CODE_LENGTH; i++) {
            sb.append(CODE_CHARS.charAt(CODE_RANDOM.nextInt(CODE_CHARS.length())));
        }
        return sb.toString();
    }

    private int getActiveStudentCount(Long courseId) {
        Long count = courseStudentMapper.selectCount(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStatus, 1)
        );
        return count == null ? 0 : count.intValue();
    }

    private java.util.Map<Long, Integer> batchStudentCount(List<Long> courseIds) {
        java.util.Map<Long, Integer> countMap = new java.util.HashMap<>();
        if (courseIds == null || courseIds.isEmpty()) {
            return countMap;
        }
        List<CourseStudent> students = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .in(CourseStudent::getCourseId, courseIds)
                        .eq(CourseStudent::getStatus, 1)
        );
        for (CourseStudent cs : students) {
            countMap.merge(cs.getCourseId(), 1, Integer::sum);
        }
        return countMap;
    }

    private CourseVO toVO(Course course, int studentCount) {
        CourseVO vo = new CourseVO();
        vo.setId(course.getId());
        vo.setName(course.getName());
        vo.setSemester(course.getSemester());
        vo.setCollege(course.getCollege());
        vo.setDescription(course.getDescription());
        vo.setCover(fileUrlSigner.sign(course.getCover()));
        vo.setCode(course.getCode());
        vo.setStatus(course.getStatus());
        vo.setTeacherId(course.getTeacherId());
        vo.setTeacherName(course.getTeacherName());
        vo.setStudentCount(studentCount);
        vo.setCreateTime(course.getCreateTime());
        vo.setUpdateTime(course.getUpdateTime());
        return vo;
    }

    @Override
    public void removeStudent(Long courseId, Long studentId, Long userId, String role) {
        courseAccessService.requireTeacher(courseId, userId, role);

        CourseStudent cs = courseStudentMapper.selectOne(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStudentId, studentId)
                        .eq(CourseStudent::getStatus, 1)
        );
        if (cs == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该学生未加入此课程或已退课");
        }

        cs.setStatus(0);
        courseStudentMapper.updateById(cs);
    }
}