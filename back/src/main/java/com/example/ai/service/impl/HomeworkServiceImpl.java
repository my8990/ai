package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.*;
import com.example.ai.entity.*;
import com.example.ai.mapper.*;
import com.example.ai.security.FileUrlSigner;
import com.example.ai.security.Roles;
import com.example.ai.service.CourseAccessService;
import com.example.ai.service.HomeworkService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class HomeworkServiceImpl implements HomeworkService {

    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private HomeworkSubmitMapper homeworkSubmitMapper;

    @Autowired
    private CourseStudentMapper courseStudentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseAccessService courseAccessService;

    @Autowired
    private FileUrlSigner fileUrlSigner;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Override
    public Homework createHomework(HomeworkCreateDTO dto, Long userId, String role) {
        courseAccessService.requireTeacher(dto.getCourseId(), userId, role);

        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "作业标题不能为空");
        }

        Homework hw = new Homework();
        hw.setCourseId(dto.getCourseId());
        hw.setTitle(dto.getTitle().trim());
        hw.setDescription(dto.getDescription());
        hw.setTeacherId(userId);
        hw.setFullScore(dto.getFullScore() == null || dto.getFullScore().compareTo(BigDecimal.ZERO) <= 0
                ? BigDecimal.valueOf(100) : dto.getFullScore());
        hw.setRefFileUrl(fileUrlSigner.normalize(dto.getRefFileUrl()));
        hw.setRefFileName(dto.getRefFileName());
        if (dto.getDeadline() != null && !dto.getDeadline().isEmpty()) {
            hw.setDeadline(LocalDateTime.parse(dto.getDeadline(), DTF));
        }
        homeworkMapper.insert(hw);
        return hw;
    }

    @Override
    public Homework updateHomework(HomeworkUpdateDTO dto, Long userId, String role) {
        Homework hw = getHomeworkOrThrow(dto.getId());
        courseAccessService.requireTeacher(hw.getCourseId(), userId, role);

        if (dto.getTitle() != null) {
            if (dto.getTitle().trim().isEmpty()) {
                throw new BizException(ErrorCode.BAD_REQUEST, "作业标题不能为空");
            }
            hw.setTitle(dto.getTitle().trim());
        }
        if (dto.getDescription() != null) {
            hw.setDescription(dto.getDescription());
        }
        if (dto.getDeadline() != null) {
            hw.setDeadline(dto.getDeadline().isEmpty() ? null : LocalDateTime.parse(dto.getDeadline(), DTF));
        }
        if (dto.getFullScore() != null) {
            hw.setFullScore(dto.getFullScore().compareTo(BigDecimal.ZERO) <= 0
                    ? BigDecimal.valueOf(100) : dto.getFullScore());
        }
        if (dto.getRefFileUrl() != null) {
            hw.setRefFileUrl(dto.getRefFileUrl().isEmpty() ? null : fileUrlSigner.normalize(dto.getRefFileUrl()));
        }
        if (dto.getRefFileName() != null) {
            hw.setRefFileName(dto.getRefFileName().isEmpty() ? null : dto.getRefFileName());
        }
        homeworkMapper.updateById(hw);
        return hw;
    }

    @Override
    public void deleteHomework(Long homeworkId, Long userId, String role) {
        Homework hw = getHomeworkOrThrow(homeworkId);
        courseAccessService.requireTeacher(hw.getCourseId(), userId, role);

        homeworkSubmitMapper.delete(
                new LambdaQueryWrapper<HomeworkSubmit>().eq(HomeworkSubmit::getHomeworkId, homeworkId)
        );
        homeworkMapper.deleteById(homeworkId);
    }

    @Override
    public List<HomeworkVO> getCourseHomework(Long courseId, Long userId, String role) {
        courseAccessService.requireMember(courseId, userId, role);

        List<Homework> list = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().eq(Homework::getCourseId, courseId).orderByDesc(Homework::getCreateTime)
        );
        int totalStudents = countNormalStudents(courseId);
        Map<Long, Integer> submitCountMap = new HashMap<>();
        if (!list.isEmpty()) {
            List<HomeworkSubmit> submits = homeworkSubmitMapper.selectList(
                    new LambdaQueryWrapper<HomeworkSubmit>().in(HomeworkSubmit::getHomeworkId, list.stream().map(Homework::getId).toList())
            );
            for (HomeworkSubmit s : submits) {
                submitCountMap.merge(s.getHomeworkId(), 1, Integer::sum);
            }
        }
        return list.stream()
                .map(hw -> toVO(hw, totalStudents, submitCountMap.getOrDefault(hw.getId(), 0)))
                .collect(Collectors.toList());
    }

    @Override
    public HomeworkVO getHomeworkDetail(Long homeworkId, Long userId, String role) {
        Homework hw = getHomeworkOrThrow(homeworkId);
        courseAccessService.requireMember(hw.getCourseId(), userId, role);

        int totalStudents = countNormalStudents(hw.getCourseId());
        return toVO(hw, totalStudents);
    }

    @Override
    public void submitHomework(HomeworkSubmitDTO dto, Long userId, String role) {
        courseAccessService.requireStudent(role);
        Homework hw = getHomeworkOrThrow(dto.getHomeworkId());
        courseAccessService.requireMember(hw.getCourseId(), userId, role);

        if (hw.getDeadline() != null && LocalDateTime.now().isAfter(hw.getDeadline())) {
            if (existingSubmissionExists(dto.getHomeworkId(), userId)) {
                throw new BizException(ErrorCode.BAD_REQUEST, "作业已过截止时间，无法修改已提交内容");
            }
        }

        boolean late = hw.getDeadline() != null && LocalDateTime.now().isAfter(hw.getDeadline());

        HomeworkSubmit existing = homeworkSubmitMapper.selectOne(
                new LambdaQueryWrapper<HomeworkSubmit>()
                        .eq(HomeworkSubmit::getHomeworkId, dto.getHomeworkId())
                        .eq(HomeworkSubmit::getStudentId, userId)
        );

        if (existing != null) {
            existing.setContent(dto.getContent());
            existing.setImages(normalizeImages(dto.getImages()));
            existing.setFileUrl(fileUrlSigner.normalize(dto.getFileUrl()));
            existing.setFileName(dto.getFileName());
            existing.setIsLate(late ? 1 : 0);
            homeworkSubmitMapper.updateById(existing);
        } else {
            HomeworkSubmit sub = new HomeworkSubmit();
            sub.setHomeworkId(dto.getHomeworkId());
            sub.setStudentId(userId);
            sub.setContent(dto.getContent());
            sub.setImages(normalizeImages(dto.getImages()));
            sub.setFileUrl(fileUrlSigner.normalize(dto.getFileUrl()));
            sub.setFileName(dto.getFileName());
            sub.setStatus(0);
            sub.setIsLate(late ? 1 : 0);
            homeworkSubmitMapper.insert(sub);
        }
    }

    private boolean existingSubmissionExists(Long homeworkId, Long studentId) {
        return homeworkSubmitMapper.selectCount(
                new LambdaQueryWrapper<HomeworkSubmit>()
                        .eq(HomeworkSubmit::getHomeworkId, homeworkId)
                        .eq(HomeworkSubmit::getStudentId, studentId)
        ) > 0;
    }

    @Override
    public void gradeHomework(HomeworkGradeDTO dto, Long userId, String role) {
        HomeworkSubmit sub = homeworkSubmitMapper.selectById(dto.getSubmitId());
        if (sub == null) throw new BizException(ErrorCode.NOT_FOUND, "提交记录不存在");
        Homework hw = getHomeworkOrThrow(sub.getHomeworkId());
        courseAccessService.requireTeacher(hw.getCourseId(), userId, role);

        if (dto.getScore() != null && dto.getScore().compareTo(BigDecimal.ZERO) < 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分数不能小于0");
        }
        BigDecimal fullScore = hw.getFullScore() == null ? BigDecimal.valueOf(100) : hw.getFullScore();
        if (dto.getScore() != null && dto.getScore().compareTo(fullScore) > 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "分数不能超过满分 " + fullScore.stripTrailingZeros().toPlainString());
        }

        sub.setScore(dto.getScore());
        sub.setComment(dto.getComment());
        sub.setStatus(1);
        homeworkSubmitMapper.updateById(sub);
    }

    @Override
    public List<HomeworkSubmitVO> getHomeworkSubmissions(Long homeworkId, Long userId, String role) {
        Homework hw = getHomeworkOrThrow(homeworkId);
        courseAccessService.requireTeacher(hw.getCourseId(), userId, role);

        List<HomeworkSubmit> list = homeworkSubmitMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmit>().eq(HomeworkSubmit::getHomeworkId, homeworkId).orderByAsc(HomeworkSubmit::getCreateTime)
        );
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> studentIds = list.stream().map(HomeworkSubmit::getStudentId).distinct().toList();
        List<User> users = userMapper.selectBatchIds(studentIds);
        Map<Long, User> userMap = new HashMap<>();
        for (User u : users) {
            userMap.put(u.getId(), u);
        }
        return list.stream().map(sub -> toSubmitVO(sub, userMap)).collect(Collectors.toList());
    }

    @Override
    public HomeworkSubmitVO getMySubmit(Long homeworkId, Long userId, String role) {
        Homework hw = getHomeworkOrThrow(homeworkId);
        courseAccessService.requireMember(hw.getCourseId(), userId, role);

        HomeworkSubmit sub = homeworkSubmitMapper.selectOne(
                new LambdaQueryWrapper<HomeworkSubmit>()
                        .eq(HomeworkSubmit::getHomeworkId, homeworkId)
                        .eq(HomeworkSubmit::getStudentId, userId)
        );
        return sub != null ? toSubmitVO(sub) : null;
    }

    @Override
    public List<HomeworkVO> getMyHomework(Long courseId, Long userId, String role) {
        courseAccessService.requireStudent(role);
        courseAccessService.requireMember(courseId, userId, role);

        List<Homework> list = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().eq(Homework::getCourseId, courseId).orderByDesc(Homework::getCreateTime)
        );
        int totalStudents = countNormalStudents(courseId);
        Map<Long, Integer> submitCountMap = new HashMap<>();
        if (!list.isEmpty()) {
            List<HomeworkSubmit> submits = homeworkSubmitMapper.selectList(
                    new LambdaQueryWrapper<HomeworkSubmit>().in(HomeworkSubmit::getHomeworkId, list.stream().map(Homework::getId).toList())
            );
            for (HomeworkSubmit s : submits) {
                submitCountMap.merge(s.getHomeworkId(), 1, Integer::sum);
            }
        }
        return list.stream()
                .map(hw -> toVO(hw, totalStudents, submitCountMap.getOrDefault(hw.getId(), 0)))
                .collect(Collectors.toList());
    }

    @Override
    public List<HomeworkVO> getMyAllHomework(Long userId, String role) {
        List<Long> courseIds;
        if (Roles.TEACHER.equals(role)) {
            List<Course> teacherCourses = courseMapper.selectList(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, userId)
                            .eq(Course::getStatus, 1)
            );
            courseIds = teacherCourses.stream().map(Course::getId).collect(Collectors.toList());
        } else if ("student".equals(role)) {
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

        List<Homework> homeworks = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>()
                        .in(Homework::getCourseId, courseIds)
                        .and(w -> w.isNull(Homework::getDeadline).or().gt(Homework::getDeadline, LocalDateTime.now()))
                        .orderByDesc(Homework::getCreateTime)
        );

        if (homeworks.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Course> courseMap = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        List<Long> teacherIds = homeworks.stream().map(Homework::getTeacherId).distinct().toList();
        Map<Long, User> teacherMap = teacherIds.isEmpty() ? new HashMap<>()
                : userMapper.selectBatchIds(teacherIds).stream()
                        .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        Map<Long, Integer> totalStudentMap = new HashMap<>();
        for (Long cid : courseIds) {
            totalStudentMap.put(cid, countNormalStudents(cid));
        }

        List<Long> hwIds = homeworks.stream().map(Homework::getId).toList();

        Map<Long, Integer> submitCountMap = new HashMap<>();
        List<HomeworkSubmit> allSubmits = homeworkSubmitMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmit>().in(HomeworkSubmit::getHomeworkId, hwIds)
        );
        for (HomeworkSubmit s : allSubmits) {
            submitCountMap.merge(s.getHomeworkId(), 1, Integer::sum);
        }

        Map<Long, HomeworkSubmit> mySubmitMap = new HashMap<>();
        for (HomeworkSubmit s : allSubmits) {
            if (s.getStudentId().equals(userId)) {
                mySubmitMap.put(s.getHomeworkId(), s);
            }
        }

        return homeworks.stream().map(hw -> {
            HomeworkVO vo = toVO(hw, totalStudentMap.getOrDefault(hw.getCourseId(), 0),
                    submitCountMap.getOrDefault(hw.getId(), 0));
            Course course = courseMap.get(hw.getCourseId());
            if (course != null) {
                vo.setCourseName(course.getName());
            }
            User teacher = teacherMap.get(hw.getTeacherId());
            if (teacher != null) {
                vo.setTeacherName(displayName(teacher));
            }
            HomeworkSubmit my = mySubmitMap.get(hw.getId());
            if (my != null) {
                vo.setSubmitted(true);
                vo.setGraded(my.getStatus() != null && my.getStatus() == 1);
                vo.setIsLate(my.getIsLate() != null && my.getIsLate() == 1);
                vo.setMyScore(my.getScore());
            } else {
                vo.setSubmitted(false);
            }
            return vo;
        }).collect(Collectors.toList());
    }

    private String displayName(User user) {
        if (user == null) return "";
        return user.getNickname() != null && !user.getNickname().isEmpty()
                ? user.getNickname() : user.getUsername();
    }

    private Homework getHomeworkOrThrow(Long homeworkId) {
        Homework hw = homeworkMapper.selectById(homeworkId);
        if (hw == null) throw new BizException(ErrorCode.NOT_FOUND, "作业不存在");
        return hw;
    }

    private HomeworkVO toVO(Homework hw, int totalStudents) {
        return toVO(hw, totalStudents, -1);
    }

    private HomeworkVO toVO(Homework hw, int totalStudents, int submitCount) {
        HomeworkVO vo = new HomeworkVO();
        BeanUtils.copyProperties(hw, vo);
        vo.setRefFileUrl(fileUrlSigner.sign(hw.getRefFileUrl()));
        if (submitCount < 0) {
            submitCount = homeworkSubmitMapper.selectCount(
                    new LambdaQueryWrapper<HomeworkSubmit>().eq(HomeworkSubmit::getHomeworkId, hw.getId())
            ).intValue();
        }
        vo.setSubmitCount(submitCount);
        vo.setTotalStudents(totalStudents);
        vo.setExpired(hw.getDeadline() != null && LocalDateTime.now().isAfter(hw.getDeadline()));
        return vo;
    }

    private HomeworkSubmitVO toSubmitVO(HomeworkSubmit sub) {
        return toSubmitVO(sub, null);
    }

    private HomeworkSubmitVO toSubmitVO(HomeworkSubmit sub, Map<Long, User> userMap) {
        HomeworkSubmitVO vo = new HomeworkSubmitVO();
        BeanUtils.copyProperties(sub, vo);
        vo.setFileUrl(fileUrlSigner.sign(sub.getFileUrl()));
        User user;
        if (userMap != null) {
            user = userMap.get(sub.getStudentId());
        } else {
            user = userMapper.selectById(sub.getStudentId());
        }
        if (user != null) {
            vo.setStudentName(user.getNickname() != null ? user.getNickname() : user.getUsername());
            vo.setStudentNo(user.getStudentNo());
        }
        if (sub.getImages() != null && !sub.getImages().isEmpty()) {
            vo.setImageList(Arrays.stream(sub.getImages().split(","))
                    .map(fileUrlSigner::sign)
                    .toList());
        }
        return vo;
    }

    private String normalizeImages(String images) {
        if (images == null || images.isBlank()) {
            return images;
        }
        return Arrays.stream(images.split(","))
                .map(fileUrlSigner::normalize)
                .collect(Collectors.joining(","));
    }

    private int countNormalStudents(Long courseId) {
        return courseStudentMapper.selectCount(
                new LambdaQueryWrapper<CourseStudent>().eq(CourseStudent::getCourseId, courseId).eq(CourseStudent::getStatus, 1)
        ).intValue();
    }
}