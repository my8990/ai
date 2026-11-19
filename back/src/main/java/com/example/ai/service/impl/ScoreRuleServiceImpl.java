package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.*;
import com.example.ai.entity.*;
import com.example.ai.mapper.*;
import com.example.ai.service.CourseAccessService;
import com.example.ai.security.Roles;
import com.example.ai.service.ScoreRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class ScoreRuleServiceImpl implements ScoreRuleService {

    @Autowired
    private ScoreRuleMapper scoreRuleMapper;

    @Autowired
    private CourseStudentMapper courseStudentMapper;

    @Autowired
    private CheckinMapper checkinMapper;

    @Autowired
    private CheckinRecordMapper checkinRecordMapper;

    @Autowired
    private HomeworkMapper homeworkMapper;

    @Autowired
    private HomeworkSubmitMapper homeworkSubmitMapper;

    @Autowired
    private QuizAssignmentMapper quizAssignmentMapper;

    @Autowired
    private QuizRecordMapper quizRecordMapper;

    @Autowired
    private QuizMapper quizMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseAccessService courseAccessService;

    @Override
    public ScoreRule setRule(ScoreRuleDTO dto, Long userId, String role) {
        courseAccessService.requireTeacher(dto.getCourseId(), userId, role);

        int checkinWeight = resolveWeight(dto.getCheckinWeight());
        int homeworkWeight = resolveWeight(dto.getHomeworkWeight());
        int quizWeight = resolveWeight(dto.getQuizWeight());
        if (checkinWeight + homeworkWeight + quizWeight != 100) {
            throw new BizException(ErrorCode.BAD_REQUEST, "签到、作业、测验占比之和必须等于100");
        }
        ScoreRule existing = scoreRuleMapper.selectOne(
                new LambdaQueryWrapper<ScoreRule>().eq(ScoreRule::getCourseId, dto.getCourseId())
        );
        if (existing != null) {
            existing.setCheckinWeight(checkinWeight);
            existing.setHomeworkWeight(homeworkWeight);
            existing.setQuizWeight(quizWeight);
            scoreRuleMapper.updateById(existing);
            return existing;
        }
        ScoreRule rule = new ScoreRule();
        rule.setCourseId(dto.getCourseId());
        rule.setCheckinWeight(checkinWeight);
        rule.setHomeworkWeight(homeworkWeight);
        rule.setQuizWeight(quizWeight);
        scoreRuleMapper.insert(rule);
        return rule;
    }

    @Override
    public ScoreRule getRule(Long courseId, Long userId, String role) {
        courseAccessService.requireMember(courseId, userId, role);
        return loadRule(courseId);
    }

    private ScoreRule loadRule(Long courseId) {
        ScoreRule rule = scoreRuleMapper.selectOne(
                new LambdaQueryWrapper<ScoreRule>().eq(ScoreRule::getCourseId, courseId)
        );
        if (rule == null) {
            rule = new ScoreRule();
            rule.setCourseId(courseId);
            rule.setCheckinWeight(40);
            rule.setHomeworkWeight(30);
            rule.setQuizWeight(30);
        }
        return rule;
    }

    @Override
    public List<StudentScoreVO> getCourseScores(Long courseId, Long userId, String role) {
        courseAccessService.requireTeacher(courseId, userId, role);

        ScoreRule rule = loadRule(courseId);

        List<Checkin> checkinList = checkinMapper.selectList(
                new LambdaQueryWrapper<Checkin>().eq(Checkin::getCourseId, courseId)
        );
        List<Long> checkinIds = checkinList.stream().map(Checkin::getId).toList();
        int totalCheckin = checkinIds.size();

        Map<Long, Integer> attendedMap = new HashMap<>();
        Map<Long, Integer> leaveMap = new HashMap<>();
        if (!checkinIds.isEmpty()) {
            List<CheckinRecord> records = checkinRecordMapper.selectList(
                    new LambdaQueryWrapper<CheckinRecord>().in(CheckinRecord::getCheckinId, checkinIds)
            );
            for (CheckinRecord record : records) {
                if (isAttendance(record.getStatus())) {
                    attendedMap.merge(record.getStudentId(), 1, Integer::sum);
                } else if (isLeave(record.getStatus())) {
                    leaveMap.merge(record.getStudentId(), 1, Integer::sum);
                }
            }
        }

        List<Homework> homeworkList = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().eq(Homework::getCourseId, courseId)
        );
        List<Long> homeworkIds = homeworkList.stream().map(Homework::getId).toList();

        Map<Long, BigDecimal> homeworkFullScoreMap = new HashMap<>();
        for (Homework hw : homeworkList) {
            homeworkFullScoreMap.put(hw.getId(), resolveFullScore(hw.getFullScore()));
        }

        Map<Long, List<BigDecimal>> homeworkScoreMap = new HashMap<>();
        if (!homeworkIds.isEmpty()) {
            List<HomeworkSubmit> submits = homeworkSubmitMapper.selectList(
                    new LambdaQueryWrapper<HomeworkSubmit>()
                            .in(HomeworkSubmit::getHomeworkId, homeworkIds)
                            .eq(HomeworkSubmit::getStatus, 1)
            );
            for (HomeworkSubmit sub : submits) {
                if (sub.getScore() != null) {
                    BigDecimal fullScore = homeworkFullScoreMap.getOrDefault(sub.getHomeworkId(), BigDecimal.valueOf(100));
                    BigDecimal normalized = sub.getScore()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(fullScore, 2, RoundingMode.HALF_UP);
                    homeworkScoreMap.computeIfAbsent(sub.getStudentId(), k -> new ArrayList<>())
                            .add(normalized);
                }
            }
        }

        List<QuizAssignment> quizAssignments = quizAssignmentMapper.selectList(
                new LambdaQueryWrapper<QuizAssignment>().eq(QuizAssignment::getCourseId, courseId)
        );
        int totalQuiz = quizAssignments.size();
        List<Long> assignmentIds = quizAssignments.stream().map(QuizAssignment::getId).toList();

        Map<Long, BigDecimal> quizScoreSumMap = new HashMap<>();
        Map<Long, Integer> quizCountMap = new HashMap<>();
        BigDecimal courseQuizFullSum = BigDecimal.ZERO;
        if (!quizAssignments.isEmpty()) {
            List<Long> quizIds = quizAssignments.stream().map(QuizAssignment::getQuizId).distinct().toList();
            Map<Long, Quiz> quizMap = quizMapper.selectBatchIds(quizIds).stream()
                    .collect(Collectors.toMap(Quiz::getId, q -> q, (a, b) -> a));
            for (QuizAssignment assignment : quizAssignments) {
                Quiz quiz = quizMap.get(assignment.getQuizId());
                courseQuizFullSum = courseQuizFullSum.add(resolveFullScore(quiz == null ? null : quiz.getFullScore()));
            }
            List<QuizRecord> quizRecords = quizRecordMapper.selectList(
                    new LambdaQueryWrapper<QuizRecord>()
                            .in(QuizRecord::getAssignmentId, assignmentIds)
                            .eq(QuizRecord::getStatus, QuizRecord.STATUS_SUBMITTED)
                            .isNotNull(QuizRecord::getScore)
            );
            for (QuizRecord record : quizRecords) {
                quizScoreSumMap.merge(record.getStudentId(), record.getScore(), BigDecimal::add);
                quizCountMap.merge(record.getStudentId(), 1, Integer::sum);
            }
        }

        List<CourseStudent> students = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStatus, 1)
        );
        if (students.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> studentIds = students.stream().map(CourseStudent::getStudentId).toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<StudentScoreVO> result = new ArrayList<>();
        for (CourseStudent cs : students) {
            StudentScoreVO vo = new StudentScoreVO();
            vo.setStudentId(cs.getStudentId());
            vo.setCheckinWeight(rule.getCheckinWeight());
            vo.setHomeworkWeight(rule.getHomeworkWeight());
            vo.setQuizWeight(rule.getQuizWeight());
            vo.setTotalQuiz(totalQuiz);

            User user = userMap.get(cs.getStudentId());
            if (user != null) {
                vo.setStudentName(user.getNickname() != null ? user.getNickname() : user.getUsername());
                vo.setStudentNo(user.getStudentNo());
            }

            int checkinCount = attendedMap.getOrDefault(cs.getStudentId(), 0);
            int leaveCount = leaveMap.getOrDefault(cs.getStudentId(), 0);
            int effectiveTotal = Math.max(totalCheckin - leaveCount, 0);
            vo.setCheckinCount(checkinCount);
            vo.setTotalCheckin(effectiveTotal);

            BigDecimal checkinScore = effectiveTotal > 0
                    ? BigDecimal.valueOf(checkinCount).multiply(BigDecimal.valueOf(100))
                    .divide(BigDecimal.valueOf(effectiveTotal), 2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;
            vo.setCheckinScore(checkinScore);

            BigDecimal homeworkScore = BigDecimal.ZERO;
            List<BigDecimal> scores = homeworkScoreMap.get(cs.getStudentId());
            if (scores != null && !scores.isEmpty()) {
                BigDecimal sum = scores.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                homeworkScore = sum.divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
            }
            vo.setHomeworkScore(homeworkScore);

            BigDecimal quizScore = BigDecimal.ZERO;
            BigDecimal quizScoreSum = quizScoreSumMap.get(cs.getStudentId());
            if (quizScoreSum != null && courseQuizFullSum.compareTo(BigDecimal.ZERO) > 0) {
                quizScore = quizScoreSum.multiply(BigDecimal.valueOf(100))
                        .divide(courseQuizFullSum, 2, RoundingMode.HALF_UP);
            }
            vo.setQuizCount(quizCountMap.getOrDefault(cs.getStudentId(), 0));
            vo.setQuizScore(quizScore);

            BigDecimal total = checkinScore.multiply(BigDecimal.valueOf(rule.getCheckinWeight()))
                    .add(homeworkScore.multiply(BigDecimal.valueOf(rule.getHomeworkWeight())))
                    .add(quizScore.multiply(BigDecimal.valueOf(resolveWeight(rule.getQuizWeight()))))
                    .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            vo.setTotalScore(total);

            result.add(vo);
        }

        result.sort((a, b) -> b.getTotalScore().compareTo(a.getTotalScore()));
        for (int i = 0; i < result.size(); i++) {
            StudentScoreVO vo = result.get(i);
            if (i > 0 && vo.getTotalScore().compareTo(result.get(i - 1).getTotalScore()) == 0) {
                vo.setRank(result.get(i - 1).getRank());
            } else {
                vo.setRank(i + 1);
            }
        }
        return result;
    }

    @Override
    public MyScoreVO getMyScore(Long courseId, Long userId, String role) {
        courseAccessService.requireStudent(role);
        courseAccessService.requireMember(courseId, userId, role);

        ScoreRule rule = loadRule(courseId);
        Course course = courseMapper.selectById(courseId);

        MyScoreVO vo = new MyScoreVO();
        vo.setCourseId(courseId);
        vo.setCourseName(course != null ? course.getName() : "");
        vo.setCheckinWeight(rule.getCheckinWeight());
        vo.setHomeworkWeight(rule.getHomeworkWeight());
        vo.setQuizWeight(rule.getQuizWeight());

        int totalCheckin = checkinMapper.selectCount(
                new LambdaQueryWrapper<Checkin>().eq(Checkin::getCourseId, courseId)
        ).intValue();

        int checkinCount = 0;
        int leaveCount = 0;
        if (totalCheckin > 0) {
            List<CheckinRecord> myRecords = checkinRecordMapper.selectList(
                    new LambdaQueryWrapper<CheckinRecord>()
                            .eq(CheckinRecord::getStudentId, userId)
                            .inSql(CheckinRecord::getCheckinId,
                                    "SELECT id FROM checkin WHERE course_id = " + courseId)
            );
            for (CheckinRecord record : myRecords) {
                if (isAttendance(record.getStatus())) {
                    checkinCount++;
                } else if (isLeave(record.getStatus())) {
                    leaveCount++;
                }
            }
        }
        int effectiveTotal = Math.max(totalCheckin - leaveCount, 0);
        vo.setCheckinCount(checkinCount);
        vo.setTotalCheckin(effectiveTotal);

        BigDecimal checkinScore = effectiveTotal > 0
                ? BigDecimal.valueOf(checkinCount).multiply(BigDecimal.valueOf(100)).divide(BigDecimal.valueOf(effectiveTotal), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        vo.setCheckinScore(checkinScore);

        List<Homework> homeworkList = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>().eq(Homework::getCourseId, courseId)
        );

        BigDecimal homeworkScore = BigDecimal.ZERO;
        if (!homeworkList.isEmpty()) {
            int gradedCount = 0;
            BigDecimal totalScore = BigDecimal.ZERO;
            for (Homework hw : homeworkList) {
                HomeworkSubmit sub = homeworkSubmitMapper.selectOne(
                        new LambdaQueryWrapper<HomeworkSubmit>()
                                .eq(HomeworkSubmit::getHomeworkId, hw.getId())
                                .eq(HomeworkSubmit::getStudentId, userId)
                                .eq(HomeworkSubmit::getStatus, 1)
                );
                if (sub != null && sub.getScore() != null) {
                    BigDecimal fullScore = resolveFullScore(hw.getFullScore());
                    BigDecimal normalized = sub.getScore()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(fullScore, 2, RoundingMode.HALF_UP);
                    totalScore = totalScore.add(normalized);
                    gradedCount++;
                }
            }
            if (gradedCount > 0) {
                homeworkScore = totalScore.divide(BigDecimal.valueOf(gradedCount), 2, RoundingMode.HALF_UP);
            }
        }
        vo.setHomeworkScore(homeworkScore);

        List<QuizAssignment> quizAssignments = quizAssignmentMapper.selectList(
                new LambdaQueryWrapper<QuizAssignment>().eq(QuizAssignment::getCourseId, courseId)
        );
        List<Long> assignmentIds = quizAssignments.stream().map(QuizAssignment::getId).toList();
        vo.setTotalQuiz(assignmentIds.size());

        BigDecimal quizScore = BigDecimal.ZERO;
        int quizCount = 0;
        if (!quizAssignments.isEmpty()) {
            List<Long> quizIds = quizAssignments.stream().map(QuizAssignment::getQuizId).distinct().toList();
            Map<Long, Quiz> quizMap = quizMapper.selectBatchIds(quizIds).stream()
                    .collect(Collectors.toMap(Quiz::getId, q -> q, (a, b) -> a));
            BigDecimal courseQuizFullSum = BigDecimal.ZERO;
            for (QuizAssignment assignment : quizAssignments) {
                Quiz quiz = quizMap.get(assignment.getQuizId());
                courseQuizFullSum = courseQuizFullSum.add(resolveFullScore(quiz == null ? null : quiz.getFullScore()));
            }

            List<QuizRecord> quizRecords = quizRecordMapper.selectList(
                    new LambdaQueryWrapper<QuizRecord>()
                            .in(QuizRecord::getAssignmentId, assignmentIds)
                            .eq(QuizRecord::getStudentId, userId)
                            .eq(QuizRecord::getStatus, QuizRecord.STATUS_SUBMITTED)
                            .isNotNull(QuizRecord::getScore)
            );
            BigDecimal quizScoreSum = BigDecimal.ZERO;
            for (QuizRecord record : quizRecords) {
                quizScoreSum = quizScoreSum.add(record.getScore());
            }
            quizCount = quizRecords.size();
            if (courseQuizFullSum.compareTo(BigDecimal.ZERO) > 0) {
                quizScore = quizScoreSum.multiply(BigDecimal.valueOf(100))
                        .divide(courseQuizFullSum, 2, RoundingMode.HALF_UP);
            }
        }
        vo.setQuizCount(quizCount);
        vo.setQuizScore(quizScore);

        BigDecimal total = checkinScore.multiply(BigDecimal.valueOf(rule.getCheckinWeight()))
                .add(homeworkScore.multiply(BigDecimal.valueOf(rule.getHomeworkWeight())))
                .add(quizScore.multiply(BigDecimal.valueOf(resolveWeight(rule.getQuizWeight()))))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        vo.setTotalScore(total);

        return vo;
    }

    @Override
    public ScoreDetailVO getScoreDetail(Long courseId, Long studentId, Long userId, String role) {
        courseAccessService.requireMember(courseId, userId, role);
        if (Roles.STUDENT.equals(role) && !userId.equals(studentId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权查看其他同学的成绩");
        }
        if (Roles.TEACHER.equals(role)) {
            courseAccessService.requireTeacher(courseId, userId, role);
        }

        ScoreRule rule = loadRule(courseId);
        Course course = courseMapper.selectById(courseId);

        ScoreDetailVO vo = new ScoreDetailVO();
        vo.setCourseId(courseId);
        vo.setCourseName(course != null ? course.getName() : "");
        vo.setStudentId(studentId);
        vo.setCheckinWeight(rule.getCheckinWeight());
        vo.setHomeworkWeight(rule.getHomeworkWeight());
        vo.setQuizWeight(rule.getQuizWeight());

        User student = userMapper.selectById(studentId);
        if (student != null) {
            vo.setStudentName(student.getNickname() != null ? student.getNickname() : student.getUsername());
            vo.setStudentNo(student.getStudentNo());
        }

        List<Checkin> checkinList = checkinMapper.selectList(
                new LambdaQueryWrapper<Checkin>()
                        .eq(Checkin::getCourseId, courseId)
                        .orderByDesc(Checkin::getCreateTime)
        );
        List<Long> checkinIds = checkinList.stream().map(Checkin::getId).toList();
        Map<Long, CheckinRecord> recordMap = new HashMap<>();
        if (!checkinIds.isEmpty()) {
            List<CheckinRecord> records = checkinRecordMapper.selectList(
                    new LambdaQueryWrapper<CheckinRecord>()
                            .in(CheckinRecord::getCheckinId, checkinIds)
                            .eq(CheckinRecord::getStudentId, studentId)
            );
            for (CheckinRecord r : records) {
                recordMap.put(r.getCheckinId(), r);
            }
        }

        int checkinCount = 0;
        int leaveCount = 0;
        for (CheckinRecord record : recordMap.values()) {
            if (isAttendance(record.getStatus())) {
                checkinCount++;
            } else if (isLeave(record.getStatus())) {
                leaveCount++;
            }
        }
        int effectiveTotal = Math.max(checkinIds.size() - leaveCount, 0);
        vo.setCheckinCount(checkinCount);
        vo.setTotalCheckin(effectiveTotal);
        BigDecimal checkinScore = effectiveTotal > 0
                ? BigDecimal.valueOf(checkinCount).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(effectiveTotal), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        vo.setCheckinScore(checkinScore);

        List<ScoreDetailVO.CheckinItem> checkinItems = new ArrayList<>();
        for (Checkin c : checkinList) {
            ScoreDetailVO.CheckinItem item = new ScoreDetailVO.CheckinItem();
            item.setCheckinId(c.getId());
            item.setTitle(c.getTitle());
            item.setCheckinTime(c.getCreateTime());
            CheckinRecord record = recordMap.get(c.getId());
            if (record != null) {
                item.setStatus(record.getStatus() == null ? 1 : record.getStatus());
                item.setSubmitTime(record.getCreateTime());
                item.setAddress(record.getAddress());
            } else {
                item.setStatus(0);
            }
            checkinItems.add(item);
        }
        vo.setCheckins(checkinItems);

        List<Homework> homeworkList = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>()
                        .eq(Homework::getCourseId, courseId)
                        .orderByDesc(Homework::getCreateTime)
        );
        List<ScoreDetailVO.HomeworkItem> homeworkItems = new ArrayList<>();
        BigDecimal scoreSum = BigDecimal.ZERO;
        int gradedCount = 0;
        for (Homework hw : homeworkList) {
            ScoreDetailVO.HomeworkItem item = new ScoreDetailVO.HomeworkItem();
            item.setHomeworkId(hw.getId());
            item.setTitle(hw.getTitle());
            BigDecimal fullScore = resolveFullScore(hw.getFullScore());
            item.setFullScore(fullScore);

            HomeworkSubmit sub = homeworkSubmitMapper.selectOne(
                    new LambdaQueryWrapper<HomeworkSubmit>()
                            .eq(HomeworkSubmit::getHomeworkId, hw.getId())
                            .eq(HomeworkSubmit::getStudentId, studentId)
            );
            if (sub == null) {
                item.setStatus(0);
            } else {
                item.setSubmitTime(sub.getCreateTime());
                item.setIsLate(sub.getIsLate() == null ? 0 : sub.getIsLate());
                if (sub.getStatus() != null && sub.getStatus() == 1 && sub.getScore() != null) {
                    item.setStatus(2);
                    item.setScore(sub.getScore());
                    item.setComment(sub.getComment());
                    BigDecimal normalized = sub.getScore()
                            .multiply(BigDecimal.valueOf(100))
                            .divide(fullScore, 2, RoundingMode.HALF_UP);
                    item.setNormalizedScore(normalized);
                    scoreSum = scoreSum.add(normalized);
                    gradedCount++;
                } else {
                    item.setStatus(1);
                }
            }
            homeworkItems.add(item);
        }
        vo.setHomeworks(homeworkItems);

        BigDecimal homeworkScore = gradedCount > 0
                ? scoreSum.divide(BigDecimal.valueOf(gradedCount), 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        vo.setHomeworkScore(homeworkScore);

        List<QuizAssignment> quizAssignments = quizAssignmentMapper.selectList(
                new LambdaQueryWrapper<QuizAssignment>()
                        .eq(QuizAssignment::getCourseId, courseId)
                        .orderByDesc(QuizAssignment::getCreateTime)
        );
        Map<Long, Quiz> quizMap = new HashMap<>();
        if (!quizAssignments.isEmpty()) {
            List<Long> quizIds = quizAssignments.stream().map(QuizAssignment::getQuizId).distinct().toList();
            quizMap = quizMapper.selectBatchIds(quizIds).stream()
                    .collect(Collectors.toMap(Quiz::getId, q -> q, (a, b) -> a));
        }

        List<ScoreDetailVO.QuizItem> quizItems = new ArrayList<>();
        BigDecimal quizScoreSum = BigDecimal.ZERO;
        BigDecimal quizFullSum = BigDecimal.ZERO;
        int quizCount = 0;
        for (QuizAssignment assignment : quizAssignments) {
            ScoreDetailVO.QuizItem item = new ScoreDetailVO.QuizItem();
            item.setAssignmentId(assignment.getId());
            item.setTitle(assignment.getTitle());
            Quiz quiz = quizMap.get(assignment.getQuizId());
            BigDecimal fullScore = resolveFullScore(quiz == null ? null : quiz.getFullScore());
            item.setFullScore(fullScore);
            quizFullSum = quizFullSum.add(fullScore);

            QuizRecord record = quizRecordMapper.selectOne(
                    new LambdaQueryWrapper<QuizRecord>()
                            .eq(QuizRecord::getAssignmentId, assignment.getId())
                            .eq(QuizRecord::getStudentId, studentId)
            );
            if (record == null || record.getStatus() == null
                    || record.getStatus() != QuizRecord.STATUS_SUBMITTED || record.getScore() == null) {
                item.setStatus(0);
            } else {
                item.setStatus(1);
                item.setScore(record.getScore());
                item.setSubmitTime(record.getSubmitAt());
                item.setNormalizedScore(record.getScore()
                        .multiply(BigDecimal.valueOf(100))
                        .divide(fullScore, 2, RoundingMode.HALF_UP));
                quizScoreSum = quizScoreSum.add(record.getScore());
                quizCount++;
            }
            quizItems.add(item);
        }
        vo.setQuizzes(quizItems);
        vo.setTotalQuiz(quizAssignments.size());
        vo.setQuizCount(quizCount);
        BigDecimal quizScore = quizFullSum.compareTo(BigDecimal.ZERO) > 0
                ? quizScoreSum.multiply(BigDecimal.valueOf(100))
                .divide(quizFullSum, 2, RoundingMode.HALF_UP)
                : BigDecimal.ZERO;
        vo.setQuizScore(quizScore);

        BigDecimal total = checkinScore.multiply(BigDecimal.valueOf(rule.getCheckinWeight()))
                .add(homeworkScore.multiply(BigDecimal.valueOf(rule.getHomeworkWeight())))
                .add(quizScore.multiply(BigDecimal.valueOf(resolveWeight(rule.getQuizWeight()))))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        vo.setTotalScore(total);
        return vo;
    }

    @Override
    public String getCourseName(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        return course == null || course.getName() == null ? "" : course.getName();
    }

    @Override
    public List<StudentScoreVO> getCourseScoresSortedByStudentNo(Long courseId, Long userId, String role) {
        List<StudentScoreVO> scores = getCourseScores(courseId, userId, role);
        scores.sort((a, b) -> {
            int cmp = compareStudentNo(a.getStudentNo(), b.getStudentNo());
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(a.getStudentId(), b.getStudentId());
        });
        return scores;
    }

    @Override
    public List<ScoreDetailExportVO> getExportDetailRows(Long courseId, Long userId, String role) {
        courseAccessService.requireTeacher(courseId, userId, role);

        List<CourseStudent> students = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStatus, 1)
        );
        if (students.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> studentIds = students.stream().map(CourseStudent::getStudentId).toList();
        Map<Long, User> userMap = userMapper.selectBatchIds(studentIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));

        List<Checkin> checkinList = checkinMapper.selectList(
                new LambdaQueryWrapper<Checkin>()
                        .eq(Checkin::getCourseId, courseId)
                        .orderByDesc(Checkin::getCreateTime)
        );
        Map<Long, Map<Long, CheckinRecord>> checkinRecordMap = new HashMap<>();
        if (!checkinList.isEmpty()) {
            List<Long> checkinIds = checkinList.stream().map(Checkin::getId).toList();
            List<CheckinRecord> records = checkinRecordMapper.selectList(
                    new LambdaQueryWrapper<CheckinRecord>()
                            .in(CheckinRecord::getCheckinId, checkinIds)
                            .in(CheckinRecord::getStudentId, studentIds)
            );
            for (CheckinRecord record : records) {
                checkinRecordMap.computeIfAbsent(record.getStudentId(), k -> new HashMap<>())
                        .put(record.getCheckinId(), record);
            }
        }

        List<Homework> homeworkList = homeworkMapper.selectList(
                new LambdaQueryWrapper<Homework>()
                        .eq(Homework::getCourseId, courseId)
                        .orderByDesc(Homework::getCreateTime)
        );
        Map<Long, Map<Long, HomeworkSubmit>> homeworkSubmitMap = new HashMap<>();
        if (!homeworkList.isEmpty()) {
            List<Long> homeworkIds = homeworkList.stream().map(Homework::getId).toList();
            List<HomeworkSubmit> submits = homeworkSubmitMapper.selectList(
                    new LambdaQueryWrapper<HomeworkSubmit>()
                            .in(HomeworkSubmit::getHomeworkId, homeworkIds)
                            .in(HomeworkSubmit::getStudentId, studentIds)
            );
            for (HomeworkSubmit submit : submits) {
                homeworkSubmitMap.computeIfAbsent(submit.getStudentId(), k -> new HashMap<>())
                        .put(submit.getHomeworkId(), submit);
            }
        }

        List<QuizAssignment> quizAssignments = quizAssignmentMapper.selectList(
                new LambdaQueryWrapper<QuizAssignment>()
                        .eq(QuizAssignment::getCourseId, courseId)
                        .orderByDesc(QuizAssignment::getCreateTime)
        );
        Map<Long, Quiz> quizMap = new HashMap<>();
        Map<Long, Map<Long, QuizRecord>> quizRecordMap = new HashMap<>();
        if (!quizAssignments.isEmpty()) {
            List<Long> quizIds = quizAssignments.stream().map(QuizAssignment::getQuizId).distinct().toList();
            quizMap = quizMapper.selectBatchIds(quizIds).stream()
                    .collect(Collectors.toMap(Quiz::getId, q -> q, (a, b) -> a));
            List<Long> assignmentIds = quizAssignments.stream().map(QuizAssignment::getId).toList();
            List<QuizRecord> quizRecords = quizRecordMapper.selectList(
                    new LambdaQueryWrapper<QuizRecord>()
                            .in(QuizRecord::getAssignmentId, assignmentIds)
                            .in(QuizRecord::getStudentId, studentIds)
            );
            for (QuizRecord record : quizRecords) {
                quizRecordMap.computeIfAbsent(record.getStudentId(), k -> new HashMap<>())
                        .put(record.getAssignmentId(), record);
            }
        }

        students.sort((a, b) -> {
            User userA = userMap.get(a.getStudentId());
            User userB = userMap.get(b.getStudentId());
            int cmp = compareStudentNo(userA == null ? null : userA.getStudentNo(),
                    userB == null ? null : userB.getStudentNo());
            if (cmp != 0) {
                return cmp;
            }
            return Long.compare(a.getStudentId(), b.getStudentId());
        });

        List<ScoreDetailExportVO> rows = new ArrayList<>();
        for (CourseStudent cs : students) {
            Long studentId = cs.getStudentId();
            User user = userMap.get(studentId);
            String studentNo = user == null || user.getStudentNo() == null ? "" : user.getStudentNo();
            String studentName = user == null ? "" :
                    (user.getNickname() != null ? user.getNickname() : user.getUsername());

            Map<Long, CheckinRecord> myCheckins = checkinRecordMap.getOrDefault(studentId, new HashMap<>());
            for (Checkin checkin : checkinList) {
                ScoreDetailExportVO row = buildDetailRow(studentNo, studentName, "签到", checkin.getTitle());
                CheckinRecord record = myCheckins.get(checkin.getId());
                if (record == null) {
                    row.setStatus("未签到");
                } else {
                    row.setStatus(checkinStatusText(record.getStatus()));
                    row.setTime(formatTime(record.getCreateTime()));
                }
                rows.add(row);
            }

            Map<Long, HomeworkSubmit> myHomeworks = homeworkSubmitMap.getOrDefault(studentId, new HashMap<>());
            for (Homework homework : homeworkList) {
                ScoreDetailExportVO row = buildDetailRow(studentNo, studentName, "作业", homework.getTitle());
                BigDecimal fullScore = resolveFullScore(homework.getFullScore());
                row.setFullScore(fullScore);
                HomeworkSubmit submit = myHomeworks.get(homework.getId());
                if (submit == null) {
                    row.setStatus("未提交");
                } else {
                    row.setTime(formatTime(submit.getCreateTime()));
                    if (submit.getStatus() != null && submit.getStatus() == 1 && submit.getScore() != null) {
                        row.setStatus("已批改");
                        row.setScore(submit.getScore());
                        row.setRate(normalizeScore(submit.getScore(), fullScore));
                    } else {
                        row.setStatus("待批改");
                    }
                }
                rows.add(row);
            }

            Map<Long, QuizRecord> myQuizzes = quizRecordMap.getOrDefault(studentId, new HashMap<>());
            for (QuizAssignment assignment : quizAssignments) {
                Quiz quiz = quizMap.get(assignment.getQuizId());
                BigDecimal fullScore = resolveFullScore(quiz == null ? null : quiz.getFullScore());
                ScoreDetailExportVO row = buildDetailRow(studentNo, studentName, "测验", assignment.getTitle());
                row.setFullScore(fullScore);
                QuizRecord record = myQuizzes.get(assignment.getId());
                if (record == null || record.getStatus() == null
                        || record.getStatus() != QuizRecord.STATUS_SUBMITTED || record.getScore() == null) {
                    row.setStatus("未作答");
                } else {
                    row.setStatus("已提交");
                    row.setScore(record.getScore());
                    row.setRate(normalizeScore(record.getScore(), fullScore));
                    row.setTime(formatTime(record.getSubmitAt()));
                }
                rows.add(row);
            }
        }
        return rows;
    }

    private ScoreDetailExportVO buildDetailRow(String studentNo, String studentName, String type, String title) {
        ScoreDetailExportVO row = new ScoreDetailExportVO();
        row.setStudentNo(studentNo);
        row.setStudentName(studentName);
        row.setType(type);
        row.setTitle(title);
        return row;
    }

    private String checkinStatusText(Integer status) {
        if (status == null) {
            return "缺勤";
        }
        if (status == 1) {
            return "正常";
        }
        if (status == 2) {
            return "迟到";
        }
        if (status == 3) {
            return "请假";
        }
        return "缺勤";
    }

    private BigDecimal normalizeScore(BigDecimal score, BigDecimal fullScore) {
        if (score == null) {
            return null;
        }
        return score.multiply(BigDecimal.valueOf(100)).divide(fullScore, 2, RoundingMode.HALF_UP);
    }

    private String formatTime(LocalDateTime time) {
        return time == null ? "" : time.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    private static int compareStudentNo(String a, String b) {
        if (a == null || b == null) {
            return a == null ? (b == null ? 0 : 1) : -1;
        }
        int i = 0;
        int j = 0;
        while (i < a.length() && j < b.length()) {
            char ca = a.charAt(i);
            char cb = b.charAt(j);
            if (Character.isDigit(ca) && Character.isDigit(cb)) {
                int startA = i;
                int startB = j;
                while (i < a.length() && Character.isDigit(a.charAt(i))) {
                    i++;
                }
                while (j < b.length() && Character.isDigit(b.charAt(j))) {
                    j++;
                }
                String numA = trimLeadingZeros(a.substring(startA, i));
                String numB = trimLeadingZeros(b.substring(startB, j));
                if (numA.length() != numB.length()) {
                    return numA.length() - numB.length();
                }
                int cmp = numA.compareTo(numB);
                if (cmp != 0) {
                    return cmp;
                }
            } else {
                if (ca != cb) {
                    return Character.compare(ca, cb);
                }
                i++;
                j++;
            }
        }
        return (a.length() - i) - (b.length() - j);
    }

    private static String trimLeadingZeros(String num) {
        int k = 0;
        while (k < num.length() - 1 && num.charAt(k) == '0') {
            k++;
        }
        return num.substring(k);
    }

    private int resolveWeight(Integer weight) {
        return weight == null ? 0 : weight;
    }

    private boolean isAttendance(Integer status) {
        return status != null && (status == 1 || status == 2);
    }

    private boolean isLeave(Integer status) {
        return status != null && status == 3;
    }

    private BigDecimal resolveFullScore(BigDecimal fullScore) {
        if (fullScore == null || fullScore.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.valueOf(100);
        }
        return fullScore;
    }
}