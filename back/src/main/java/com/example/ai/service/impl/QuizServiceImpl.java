package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.*;
import com.example.ai.entity.*;
import com.example.ai.mapper.*;
import com.example.ai.service.CourseAccessService;
import com.example.ai.security.Roles;
import com.example.ai.service.QuizService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QuizServiceImpl implements QuizService {

    @Autowired
    private QuizMapper quizMapper;

    @Autowired
    private QuizQuestionMapper quizQuestionMapper;

    @Autowired
    private QuizRecordMapper quizRecordMapper;

    @Autowired
    private QuizAnswerMapper quizAnswerMapper;

    @Autowired
    private QuizAssignmentMapper quizAssignmentMapper;

    @Autowired
    private QuizFavoriteMapper quizFavoriteMapper;

    @Autowired
    private CourseMapper courseMapper;

    @Autowired
    private CourseStudentMapper courseStudentMapper;

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private NoticeMapper noticeMapper;

    @Autowired
    private CourseAccessService courseAccessService;

    private static final DateTimeFormatter DTF = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final ObjectMapper JSON = new ObjectMapper();

    // ==================== 题库（模板） ====================

    @Override
    @Transactional
    public QuizVO createQuiz(QuizCreateDTO dto, Long userId, String role) {
        requireTeacherRole(role);
        if (dto.getCourseId() != null) {
            courseAccessService.requireTeacher(dto.getCourseId(), userId, role);
        }
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验标题不能为空");
        }
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验至少需要一道题目");
        }

        Quiz quiz = new Quiz();
        quiz.setCourseId(dto.getCourseId());
        quiz.setTeacherId(userId);
        quiz.setTitle(dto.getTitle().trim());
        quiz.setDescription(dto.getDescription());
        quiz.setDurationMinutes(dto.getDurationMinutes() == null ? 0 : dto.getDurationMinutes());
        quiz.setStartTime(parseTime(dto.getStartTime()));
        quiz.setEndTime(parseTime(dto.getEndTime()));
        quiz.setShowAnswerAfter(dto.getShowAnswerAfter() != null && dto.getShowAnswerAfter() ? 1 : 0);
        quiz.setStatus(Quiz.STATUS_DRAFT);
        quiz.setVisibility(Quiz.VISIBILITY_PRIVATE);
        quiz.setFavoriteCount(0);
        quiz.setUseCount(0);
        quiz.setFullScore(dto.getFullScore() != null ? dto.getFullScore() : sumScore(dto.getQuestions()));
        quizMapper.insert(quiz);
        saveQuestions(quiz.getId(), dto.getQuestions());
        return buildTemplateVO(quiz, userId, false);
    }

    @Override
    @Transactional
    public QuizVO updateQuiz(QuizCreateDTO dto, Long userId, String role) {
        requireTeacherRole(role);
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验ID不能为空");
        }
        Quiz quiz = getQuizOrThrow(dto.getId());
        requireQuizOwner(quiz, userId);
        if (quiz.getStatus() != Quiz.STATUS_DRAFT) {
            throw new BizException(ErrorCode.FORBIDDEN, "该测验已就绪并已用于发布，不能再修改题目");
        }
        if (dto.getTitle() == null || dto.getTitle().trim().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验标题不能为空");
        }
        if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验至少需要一道题目");
        }

        quiz.setTitle(dto.getTitle().trim());
        quiz.setDescription(dto.getDescription());
        quiz.setDurationMinutes(dto.getDurationMinutes() == null ? 0 : dto.getDurationMinutes());
        quiz.setStartTime(parseTime(dto.getStartTime()));
        quiz.setEndTime(parseTime(dto.getEndTime()));
        quiz.setShowAnswerAfter(dto.getShowAnswerAfter() != null && dto.getShowAnswerAfter() ? 1 : 0);
        quiz.setFullScore(dto.getFullScore() != null ? dto.getFullScore() : sumScore(dto.getQuestions()));
        quizMapper.updateById(quiz);

        quizQuestionMapper.delete(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuizId, quiz.getId()));
        saveQuestions(quiz.getId(), dto.getQuestions());
        return buildTemplateVO(quiz, userId, isFavorited(quiz.getId(), userId));
    }

    @Override
    @Transactional
    public void publishQuiz(Long quizId, Long userId, String role) {
        requireTeacherRole(role);
        Quiz quiz = getQuizOrThrow(quizId);
        requireQuizOwner(quiz, userId);
        if (quiz.getStatus() == Quiz.STATUS_READY) {
            throw new BizException(ErrorCode.CONFLICT, "该测验已就绪");
        }
        if (countQuestionsFor(quizId) == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验至少需要一道题目");
        }
        quiz.setStatus(Quiz.STATUS_READY);
        quizMapper.updateById(quiz);
    }

    @Override
    @Transactional
    public void deleteQuiz(Long quizId, Long userId, String role) {
        requireTeacherRole(role);
        Quiz quiz = getQuizOrThrow(quizId);
        requireQuizOwner(quiz, userId);
        Long assignments = quizAssignmentMapper.selectCount(new LambdaQueryWrapper<QuizAssignment>()
                .eq(QuizAssignment::getQuizId, quizId));
        if (assignments != null && assignments > 0) {
            throw new BizException(ErrorCode.CONFLICT, "请先删除该测验在各课程中的发布记录");
        }
        quizQuestionMapper.delete(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuizId, quizId));
        quizFavoriteMapper.delete(new LambdaQueryWrapper<QuizFavorite>()
                .eq(QuizFavorite::getQuizId, quizId));
        quizMapper.deleteById(quizId);
    }

    @Override
    public List<QuizVO> getMyQuizzes(Long userId, String role) {
        requireTeacherRole(role);
        List<Quiz> quizzes = quizMapper.selectList(new LambdaQueryWrapper<Quiz>()
                .eq(Quiz::getTeacherId, userId)
                .orderByDesc(Quiz::getCreateTime));
        return buildTemplateVOList(quizzes, userId);
    }

    @Override
    public QuizVO getQuizDetail(Long quizId, Long userId, String role) {
        Quiz quiz = getQuizOrThrow(quizId);
        List<QuizQuestion> questions = quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuizId, quizId)
                .orderByAsc(QuizQuestion::getSortOrder));

        if (Roles.TEACHER.equals(role)) {
            boolean owner = quiz.getTeacherId() != null && quiz.getTeacherId().equals(userId);
            QuizVO vo = buildTemplateVO(quiz, userId, isFavorited(quizId, userId));
            vo.setQuestionCount(questions.size());
            if (owner) {
                vo.setQuestions(questions.stream()
                        .map(q -> toQuestionVO(q, true, null))
                        .collect(Collectors.toList()));
            } else if (quiz.getVisibility() != null && quiz.getVisibility() == Quiz.VISIBILITY_PUBLIC) {
                vo.setQuestions(questions.stream()
                        .map(q -> toQuestionVO(q, false, null))
                        .collect(Collectors.toList()));
            } else {
                throw new BizException(ErrorCode.FORBIDDEN, "该测验未公开，无权查看");
            }
            return vo;
        }
        throw new BizException(ErrorCode.FORBIDDEN, "无权查看测验题目");
    }

    // ==================== 教师共享 / 收藏 ====================

    @Override
    @Transactional
    public void shareQuiz(Long quizId, boolean share, Long userId, String role) {
        requireTeacherRole(role);
        Quiz quiz = getQuizOrThrow(quizId);
        requireQuizOwner(quiz, userId);
        if (share) {
            if (countQuestionsFor(quizId) == 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "空测验不能公开");
            }
            quiz.setVisibility(Quiz.VISIBILITY_PUBLIC);
            quiz.setSharedTime(LocalDateTime.now());
        } else {
            quiz.setVisibility(Quiz.VISIBILITY_PRIVATE);
        }
        quizMapper.updateById(quiz);
    }

    @Override
    public List<QuizVO> getMarketQuizzes(String keyword, String sort, Long userId, String role) {
        requireTeacherRole(role);
        LambdaQueryWrapper<Quiz> wrapper = new LambdaQueryWrapper<Quiz>()
                .eq(Quiz::getVisibility, Quiz.VISIBILITY_PUBLIC);
        if (keyword != null && !keyword.trim().isEmpty()) {
            String kw = keyword.trim();
            wrapper.and(w -> w.like(Quiz::getTitle, kw).or().like(Quiz::getDescription, kw));
        }
        if ("hot".equals(sort)) {
            wrapper.orderByDesc(Quiz::getFavoriteCount).orderByDesc(Quiz::getUseCount);
        } else {
            wrapper.orderByDesc(Quiz::getSharedTime).orderByDesc(Quiz::getCreateTime);
        }
        List<Quiz> quizzes = quizMapper.selectList(wrapper);
        return buildTemplateVOList(quizzes, userId);
    }

    @Override
    @Transactional
    public void favoriteQuiz(Long quizId, Long userId, String role) {
        requireTeacherRole(role);
        Quiz quiz = getQuizOrThrow(quizId);
        if (quiz.getVisibility() == null || quiz.getVisibility() != Quiz.VISIBILITY_PUBLIC) {
            throw new BizException(ErrorCode.FORBIDDEN, "该测验未公开，无法收藏");
        }
        if (quiz.getTeacherId() != null && quiz.getTeacherId().equals(userId)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "无需收藏自己的测验");
        }
        if (isFavorited(quizId, userId)) {
            throw new BizException(ErrorCode.CONFLICT, "已收藏该测验");
        }
        QuizFavorite favorite = new QuizFavorite();
        favorite.setTeacherId(userId);
        favorite.setQuizId(quizId);
        quizFavoriteMapper.insert(favorite);

        quiz.setFavoriteCount((quiz.getFavoriteCount() == null ? 0 : quiz.getFavoriteCount()) + 1);
        quizMapper.updateById(quiz);
    }

    @Override
    @Transactional
    public void unfavoriteQuiz(Long quizId, Long userId, String role) {
        requireTeacherRole(role);
        QuizFavorite favorite = quizFavoriteMapper.selectOne(new LambdaQueryWrapper<QuizFavorite>()
                .eq(QuizFavorite::getTeacherId, userId)
                .eq(QuizFavorite::getQuizId, quizId));
        if (favorite == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "尚未收藏该测验");
        }
        quizFavoriteMapper.deleteById(favorite.getId());
        Quiz quiz = quizMapper.selectById(quizId);
        if (quiz != null && quiz.getFavoriteCount() != null && quiz.getFavoriteCount() > 0) {
            quiz.setFavoriteCount(quiz.getFavoriteCount() - 1);
            quizMapper.updateById(quiz);
        }
    }

    @Override
    public List<QuizVO> getMyFavorites(Long userId, String role) {
        requireTeacherRole(role);
        List<QuizFavorite> favorites = quizFavoriteMapper.selectList(new LambdaQueryWrapper<QuizFavorite>()
                .eq(QuizFavorite::getTeacherId, userId)
                .orderByDesc(QuizFavorite::getCreateTime));
        if (favorites.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> quizIds = favorites.stream().map(QuizFavorite::getQuizId).collect(Collectors.toList());
        List<Quiz> quizzes = quizMapper.selectBatchIds(quizIds);
        List<QuizVO> list = buildTemplateVOList(quizzes, userId);
        for (QuizVO vo : list) {
            vo.setFavorited(true);
        }
        return list;
    }

    @Override
    @Transactional
    public QuizVO forkQuiz(Long quizId, Long userId, String role) {
        requireTeacherRole(role);
        Quiz source = getQuizOrThrow(quizId);
        if (source.getTeacherId() != null && source.getTeacherId().equals(userId)) {
            return buildTemplateVO(source, userId, false);
        }
        if (source.getVisibility() == null || source.getVisibility() != Quiz.VISIBILITY_PUBLIC) {
            throw new BizException(ErrorCode.FORBIDDEN, "该测验未公开，无法引用");
        }
        if (countQuestionsFor(quizId) == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该测验没有题目，无法引用");
        }

        Quiz copy = new Quiz();
        BeanUtils.copyProperties(source, copy, "id", "createTime", "updateTime", "deleted");
        copy.setTeacherId(userId);
        copy.setCourseId(null);
        copy.setSourceQuizId(source.getId());
        copy.setStatus(Quiz.STATUS_DRAFT);
        copy.setVisibility(Quiz.VISIBILITY_PRIVATE);
        copy.setFavoriteCount(0);
        copy.setUseCount(0);
        copy.setSharedTime(null);
        quizMapper.insert(copy);

        List<QuizQuestion> questions = quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuizId, quizId)
                .orderByAsc(QuizQuestion::getSortOrder));
        for (QuizQuestion q : questions) {
            QuizQuestion nq = new QuizQuestion();
            BeanUtils.copyProperties(q, nq, "id");
            nq.setQuizId(copy.getId());
            quizQuestionMapper.insert(nq);
        }

        source.setUseCount((source.getUseCount() == null ? 0 : source.getUseCount()) + 1);
        quizMapper.updateById(source);
        return buildTemplateVO(copy, userId, false);
    }

    // ==================== 发布实例 ====================

    @Override
    @Transactional
    public QuizVO assignQuiz(QuizAssignDTO dto, Long userId, String role) {
        requireTeacherRole(role);
        if (dto.getQuizId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验ID不能为空");
        }
        if (dto.getCourseId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课程ID不能为空");
        }
        Course course = courseAccessService.requireTeacher(dto.getCourseId(), userId, role);
        Quiz quiz = getQuizOrThrow(dto.getQuizId());
        requireQuizOwner(quiz, userId);
        if (countQuestionsFor(quiz.getId()) == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验至少需要一道题目");
        }
        Long exists = quizAssignmentMapper.selectCount(new LambdaQueryWrapper<QuizAssignment>()
                .eq(QuizAssignment::getQuizId, quiz.getId())
                .eq(QuizAssignment::getCourseId, dto.getCourseId()));
        if (exists != null && exists > 0) {
            throw new BizException(ErrorCode.CONFLICT, "该测验已发布到本课程，请勿重复发布");
        }

        LocalDateTime start = dto.getStartTime() == null || dto.getStartTime().trim().isEmpty()
                ? quiz.getStartTime() : parseTime(dto.getStartTime());
        LocalDateTime end = dto.getEndTime() == null || dto.getEndTime().trim().isEmpty()
                ? quiz.getEndTime() : parseTime(dto.getEndTime());
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "开始时间不能晚于截止时间");
        }

        boolean publishNow = dto.getPublishNow() != null && dto.getPublishNow();
        QuizAssignment assignment = new QuizAssignment();
        assignment.setQuizId(quiz.getId());
        assignment.setCourseId(dto.getCourseId());
        assignment.setTeacherId(userId);
        assignment.setTitle(dto.getTitle() == null || dto.getTitle().trim().isEmpty()
                ? quiz.getTitle() : dto.getTitle().trim());
        assignment.setDurationMinutes(dto.getDurationMinutes() == null
                ? (quiz.getDurationMinutes() == null ? 0 : quiz.getDurationMinutes())
                : dto.getDurationMinutes());
        assignment.setStartTime(start);
        assignment.setEndTime(end);
        assignment.setStatus(publishNow ? QuizAssignment.STATUS_OPEN : QuizAssignment.STATUS_PENDING);
        quizAssignmentMapper.insert(assignment);

        if (quiz.getStatus() == Quiz.STATUS_DRAFT) {
            quiz.setStatus(Quiz.STATUS_READY);
            quizMapper.updateById(quiz);
        }
        if (publishNow) {
            notifyStudents(assignment, course);
        }
        QuizRecord record = null;
        return buildAssignmentVO(quiz, assignment, course, record,
                countQuestionsFor(quiz.getId()), userId, role, null);
    }

    @Override
    @Transactional
    public QuizVO updateAssignment(QuizAssignDTO dto, Long userId, String role) {
        requireTeacherRole(role);
        if (dto.getId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "发布记录ID不能为空");
        }
        QuizAssignment assignment = getAssignmentOrThrow(dto.getId());
        requireAssignmentOwner(assignment, userId);
        if (assignment.getStatus() != QuizAssignment.STATUS_PENDING) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能修改未发布的测验");
        }
        LocalDateTime start = dto.getStartTime() == null || dto.getStartTime().trim().isEmpty()
                ? assignment.getStartTime() : parseTime(dto.getStartTime());
        LocalDateTime end = dto.getEndTime() == null || dto.getEndTime().trim().isEmpty()
                ? assignment.getEndTime() : parseTime(dto.getEndTime());
        if (start != null && end != null && start.isAfter(end)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "开始时间不能晚于截止时间");
        }
        if (dto.getTitle() != null && !dto.getTitle().trim().isEmpty()) {
            assignment.setTitle(dto.getTitle().trim());
        }
        if (dto.getDurationMinutes() != null) {
            assignment.setDurationMinutes(dto.getDurationMinutes());
        }
        assignment.setStartTime(start);
        assignment.setEndTime(end);
        quizAssignmentMapper.updateById(assignment);

        Quiz quiz = getQuizOrThrow(assignment.getQuizId());
        Course course = courseMapper.selectById(assignment.getCourseId());
        return buildAssignmentVO(quiz, assignment, course, null,
                countQuestionsFor(quiz.getId()), userId, role, null);
    }

    @Override
    @Transactional
    public void publishAssignment(Long assignmentId, Long userId, String role) {
        requireTeacherRole(role);
        QuizAssignment assignment = getAssignmentOrThrow(assignmentId);
        requireAssignmentOwner(assignment, userId);
        if (assignment.getStatus() == QuizAssignment.STATUS_OPEN) {
            throw new BizException(ErrorCode.CONFLICT, "该测验已发布");
        }
        if (assignment.getStatus() == QuizAssignment.STATUS_ENDED) {
            throw new BizException(ErrorCode.CONFLICT, "该测验已结束，不能再次发布");
        }
        assignment.setStatus(QuizAssignment.STATUS_OPEN);
        quizAssignmentMapper.updateById(assignment);
        notifyStudents(assignment, courseMapper.selectById(assignment.getCourseId()));
    }

    @Override
    @Transactional
    public void endAssignment(Long assignmentId, Long userId, String role) {
        requireTeacherRole(role);
        QuizAssignment assignment = getAssignmentOrThrow(assignmentId);
        requireAssignmentOwner(assignment, userId);
        if (assignment.getStatus() != QuizAssignment.STATUS_OPEN) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该测验当前不是进行中状态");
        }
        assignment.setStatus(QuizAssignment.STATUS_ENDED);
        quizAssignmentMapper.updateById(assignment);
    }

    @Override
    @Transactional
    public void deleteAssignment(Long assignmentId, Long userId, String role) {
        requireTeacherRole(role);
        QuizAssignment assignment = getAssignmentOrThrow(assignmentId);
        requireAssignmentOwner(assignment, userId);
        if (assignment.getStatus() == QuizAssignment.STATUS_OPEN) {
            throw new BizException(ErrorCode.CONFLICT, "请先结束该测验再删除");
        }
        List<QuizRecord> records = quizRecordMapper.selectList(new LambdaQueryWrapper<QuizRecord>()
                .eq(QuizRecord::getAssignmentId, assignmentId));
        if (!records.isEmpty()) {
            List<Long> recordIds = records.stream().map(QuizRecord::getId).collect(Collectors.toList());
            quizAnswerMapper.delete(new LambdaQueryWrapper<QuizAnswer>()
                    .in(QuizAnswer::getRecordId, recordIds));
            quizRecordMapper.delete(new LambdaQueryWrapper<QuizRecord>()
                    .eq(QuizRecord::getAssignmentId, assignmentId));
        }
        quizAssignmentMapper.physicalDeleteById(assignmentId);
    }

    @Override
    public List<QuizVO> getCourseQuizzes(Long courseId, Long userId, String role) {
        Course course = courseAccessService.requireMember(courseId, userId, role);
        boolean teacher = Roles.TEACHER.equals(role);
        LambdaQueryWrapper<QuizAssignment> wrapper = new LambdaQueryWrapper<QuizAssignment>()
                .eq(QuizAssignment::getCourseId, courseId)
                .orderByDesc(QuizAssignment::getCreateTime);
        if (!teacher) {
            wrapper.in(QuizAssignment::getStatus,
                    QuizAssignment.STATUS_OPEN, QuizAssignment.STATUS_ENDED);
        }
        List<QuizAssignment> assignments = quizAssignmentMapper.selectList(wrapper);
        if (assignments.isEmpty()) {
            return new ArrayList<>();
        }
        return buildAssignmentVOList(assignments, course, userId, role);
    }

    // ==================== 学生答题 ====================

    @Override
    @Transactional
    public QuizTakeVO takeQuiz(Long quizId, Long assignmentId, Long userId, String role) {
        courseAccessService.requireStudent(role);

        QuizAssignment assignment = resolveAssignment(quizId, assignmentId, userId);
        Quiz quiz = getQuizOrThrow(assignment.getQuizId());
        courseAccessService.requireMember(assignment.getCourseId(), userId, role);

        if (assignment.getStatus() != QuizAssignment.STATUS_OPEN) {
            throw new BizException(ErrorCode.FORBIDDEN, "测验尚未发布或已结束");
        }
        LocalDateTime now = LocalDateTime.now();
        if (assignment.getStartTime() != null && now.isBefore(assignment.getStartTime())) {
            throw new BizException(ErrorCode.FORBIDDEN, "测验尚未开始");
        }
        if (assignment.getEndTime() != null && now.isAfter(assignment.getEndTime())) {
            throw new BizException(ErrorCode.FORBIDDEN, "测验已结束");
        }

        QuizRecord record = findRecordByAssignment(assignment.getId(), userId);
        if (record != null && record.getStatus() == QuizRecord.STATUS_SUBMITTED) {
            throw new BizException(ErrorCode.CONFLICT, "你已提交过该测验");
        }
        if (record == null) {
            record = new QuizRecord();
            record.setQuizId(quiz.getId());
            record.setAssignmentId(assignment.getId());
            record.setStudentId(userId);
            record.setStatus(QuizRecord.STATUS_DOING);
            record.setStartAt(now);
            quizRecordMapper.insert(record);
        }

        List<QuizQuestion> questions = quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuizId, quiz.getId())
                .orderByAsc(QuizQuestion::getSortOrder));

        QuizTakeVO vo = new QuizTakeVO();
        vo.setQuizId(quiz.getId());
        vo.setAssignmentId(assignment.getId());
        vo.setRecordId(record.getId());
        vo.setTitle(assignment.getTitle() == null ? quiz.getTitle() : assignment.getTitle());
        vo.setDescription(quiz.getDescription());
        vo.setDurationMinutes(assignment.getDurationMinutes());
        vo.setStartTime(assignment.getStartTime());
        vo.setEndTime(assignment.getEndTime());
        vo.setFullScore(quiz.getFullScore());
        vo.setRemainingSeconds(calcRemaining(assignment.getDurationMinutes(), assignment.getEndTime(), record, now));
        vo.setQuestions(questions.stream().map(q -> toQuestionVO(q, false, null)).collect(Collectors.toList()));
        return vo;
    }

    @Override
    @Transactional
    public QuizRecordVO submitQuiz(QuizSubmitDTO dto, Long userId, String role) {
        courseAccessService.requireStudent(role);

        QuizRecord record = null;
        if (dto.getAssignmentId() != null) {
            record = findRecordByAssignment(dto.getAssignmentId(), userId);
        }
        if (record == null && dto.getQuizId() != null) {
            record = findRecord(dto.getQuizId(), userId);
        }
        if (record == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请先开始答题");
        }
        Quiz quiz = getQuizOrThrow(record.getQuizId());

        QuizAssignment assignment = null;
        if (record.getAssignmentId() != null) {
            assignment = quizAssignmentMapper.selectById(record.getAssignmentId());
            if (assignment != null) {
                courseAccessService.requireMember(assignment.getCourseId(), userId, role);
                if (assignment.getStatus() == QuizAssignment.STATUS_ENDED) {
                    throw new BizException(ErrorCode.FORBIDDEN, "测验已结束，无法提交");
                }
            }
        }
        if (record.getStatus() == QuizRecord.STATUS_SUBMITTED) {
            throw new BizException(ErrorCode.CONFLICT, "你已提交过该测验");
        }

        List<QuizQuestion> questions = quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuizId, quiz.getId())
                .orderByAsc(QuizQuestion::getSortOrder));
        Map<Long, String> answerMap = new HashMap<>();
        if (dto.getAnswers() != null) {
            for (QuizAnswerDTO a : dto.getAnswers()) {
                if (a.getQuestionId() != null) {
                    answerMap.put(a.getQuestionId(), a.getAnswer());
                }
            }
        }

        BigDecimal total = BigDecimal.ZERO;
        List<QuizAnswer> details = new ArrayList<>();
        try {
            for (QuizQuestion q : questions) {
                String raw = answerMap.get(q.getId());
                String normalized = normalizeAnswer(q.getType(), raw);
                boolean correct = judge(q, normalized);
                BigDecimal got = correct ? (q.getScore() == null ? BigDecimal.ZERO : q.getScore()) : BigDecimal.ZERO;

                QuizAnswer detail = new QuizAnswer();
                detail.setRecordId(record.getId());
                detail.setQuestionId(q.getId());
                detail.setAnswer(normalized);
                detail.setIsCorrect(correct ? 1 : 0);
                detail.setScore(got);
                quizAnswerMapper.insert(detail);
                details.add(detail);
                total = total.add(got);
            }
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "你已提交过该测验");
        }

        record.setScore(total);
        record.setStatus(QuizRecord.STATUS_SUBMITTED);
        record.setSubmitAt(LocalDateTime.now());
        quizRecordMapper.updateById(record);

        return buildRecordVO(quiz, record, questions, details);
    }

    @Override
    public QuizRecordVO getMyRecord(Long quizId, Long assignmentId, Long userId, String role) {
        courseAccessService.requireStudent(role);
        QuizAssignment assignment = resolveAssignment(quizId, assignmentId, userId);
        Quiz quiz = getQuizOrThrow(assignment.getQuizId());
        courseAccessService.requireMember(assignment.getCourseId(), userId, role);

        QuizRecord record = findRecordByAssignment(assignment.getId(), userId);
        if (record == null || record.getStatus() != QuizRecord.STATUS_SUBMITTED) {
            throw new BizException(ErrorCode.NOT_FOUND, "暂无提交记录");
        }
        List<QuizQuestion> questions = quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuizId, quiz.getId())
                .orderByAsc(QuizQuestion::getSortOrder));
        List<QuizAnswer> details = quizAnswerMapper.selectList(new LambdaQueryWrapper<QuizAnswer>()
                .eq(QuizAnswer::getRecordId, record.getId()));
        return buildRecordVO(quiz, record, questions, details);
    }

    @Override
    public List<QuizVO> getMyAllQuizzes(Long userId, String role) {
        if (Roles.TEACHER.equals(role)) {
            return getMyQuizzes(userId, role);
        }
        List<Long> courseIds = courseStudentMapper.selectList(new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getStudentId, userId)
                        .eq(CourseStudent::getStatus, 1))
                .stream().map(CourseStudent::getCourseId).collect(Collectors.toList());
        if (courseIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<QuizAssignment> assignments = quizAssignmentMapper.selectList(new LambdaQueryWrapper<QuizAssignment>()
                .in(QuizAssignment::getCourseId, courseIds)
                .in(QuizAssignment::getStatus, QuizAssignment.STATUS_OPEN, QuizAssignment.STATUS_ENDED)
                .orderByDesc(QuizAssignment::getCreateTime)
                .last("LIMIT 50"));
        if (assignments.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, Course> courseMap = courseMapper.selectBatchIds(
                        assignments.stream().map(QuizAssignment::getCourseId).distinct()
                                .collect(Collectors.toList()))
                .stream().collect(Collectors.toMap(Course::getId, c -> c));
        return buildAssignmentVOListMulti(assignments, courseMap, userId, role);
    }

    // ==================== 内部工具 ====================

    private void requireTeacherRole(String role) {
        if (!Roles.TEACHER.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有教师才能执行该操作");
        }
    }

    private void requireQuizOwner(Quiz quiz, Long userId) {
        if (quiz.getTeacherId() == null || !quiz.getTeacherId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己创建的测验");
        }
    }

    private void requireAssignmentOwner(QuizAssignment assignment, Long userId) {
        if (assignment.getTeacherId() == null || !assignment.getTeacherId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能操作自己发布的测验");
        }
    }

    private QuizAssignment getAssignmentOrThrow(Long assignmentId) {
        QuizAssignment assignment = quizAssignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "测验发布记录不存在");
        }
        return assignment;
    }

    /**
     * 定位学生要作答的发布实例：新客户端同时传模板ID与实例ID，老客户端只传模板ID。
     */
    private QuizAssignment resolveAssignment(Long quizId, Long assignmentId, Long studentId) {
        if (quizId == null && assignmentId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验ID不能为空");
        }
        if (assignmentId != null) {
            QuizAssignment assignment = quizAssignmentMapper.selectById(assignmentId);
            if (assignment == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "测验发布记录不存在");
            }
            if (quizId != null && !quizId.equals(assignment.getQuizId())) {
                throw new BizException(ErrorCode.BAD_REQUEST, "测验与发布记录不匹配");
            }
            return assignment;
        }
        List<Long> courseIds = courseStudentMapper.selectList(new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getStudentId, studentId)
                        .eq(CourseStudent::getStatus, 1))
                .stream().map(CourseStudent::getCourseId).collect(Collectors.toList());
        if (courseIds.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "测验不存在");
        }
        List<QuizAssignment> candidates = quizAssignmentMapper.selectList(new LambdaQueryWrapper<QuizAssignment>()
                .eq(QuizAssignment::getQuizId, quizId)
                .in(QuizAssignment::getCourseId, courseIds)
                .in(QuizAssignment::getStatus, QuizAssignment.STATUS_OPEN, QuizAssignment.STATUS_ENDED)
                .orderByDesc(QuizAssignment::getCreateTime));
        if (candidates.isEmpty()) {
            throw new BizException(ErrorCode.NOT_FOUND, "测验不存在或未发布到你的课程");
        }
        for (QuizAssignment c : candidates) {
            if (c.getStatus() == QuizAssignment.STATUS_OPEN) {
                return c;
            }
        }
        return candidates.get(0);
    }

    private BigDecimal sumScore(List<QuizQuestionDTO> questions) {
        BigDecimal total = BigDecimal.ZERO;
        for (QuizQuestionDTO q : questions) {
            total = total.add(q.getScore() == null ? BigDecimal.valueOf(5) : q.getScore());
        }
        return total;
    }

    private void saveQuestions(Long quizId, List<QuizQuestionDTO> dtos) {
        int index = 0;
        for (QuizQuestionDTO dto : dtos) {
            QuizQuestion q = new QuizQuestion();
            q.setQuizId(quizId);
            q.setType(dto.getType() == null ? QuizQuestion.TYPE_SINGLE : dto.getType());
            q.setContent(dto.getContent() == null ? "" : dto.getContent().trim());
            List<String> options = dto.getOptions();
            if (q.getType() == QuizQuestion.TYPE_JUDGE && (options == null || options.isEmpty())) {
                options = List.of("正确", "错误");
            }
            q.setOptions(options == null || options.isEmpty() ? null : toJson(options));
            q.setCorrectAnswer(dto.getCorrectAnswer() == null ? "" : dto.getCorrectAnswer().trim());
            q.setScore(dto.getScore() == null ? BigDecimal.valueOf(5) : dto.getScore());
            q.setAnalysis(dto.getAnalysis());
            q.setSortOrder(dto.getSortOrder() == null ? index : dto.getSortOrder());
            quizQuestionMapper.insert(q);
            index++;
        }
    }

    private int countQuestionsFor(Long quizId) {
        Long count = quizQuestionMapper.selectCount(new LambdaQueryWrapper<QuizQuestion>()
                .eq(QuizQuestion::getQuizId, quizId));
        return count == null ? 0 : count.intValue();
    }

    private Map<Long, Integer> countQuestions(List<Long> quizIds) {
        if (quizIds == null || quizIds.isEmpty()) {
            return new HashMap<>();
        }
        List<QuizQuestion> questions = quizQuestionMapper.selectList(new LambdaQueryWrapper<QuizQuestion>()
                .in(QuizQuestion::getQuizId, quizIds));
        Map<Long, Integer> map = new HashMap<>();
        for (QuizQuestion q : questions) {
            map.merge(q.getQuizId(), 1, Integer::sum);
        }
        return map;
    }

    private Map<Long, QuizRecord> loadMyRecordsByAssignment(List<Long> assignmentIds, Long studentId) {
        if (assignmentIds == null || assignmentIds.isEmpty()) {
            return new HashMap<>();
        }
        List<QuizRecord> records = quizRecordMapper.selectList(new LambdaQueryWrapper<QuizRecord>()
                .in(QuizRecord::getAssignmentId, assignmentIds)
                .eq(QuizRecord::getStudentId, studentId));
        return records.stream()
                .filter(r -> r.getAssignmentId() != null)
                .collect(Collectors.toMap(QuizRecord::getAssignmentId, r -> r, (a, b) -> a));
    }

    private QuizRecord findRecord(Long quizId, Long studentId) {
        return quizRecordMapper.selectOne(new LambdaQueryWrapper<QuizRecord>()
                .eq(QuizRecord::getQuizId, quizId)
                .eq(QuizRecord::getStudentId, studentId)
                .orderByDesc(QuizRecord::getId)
                .last("LIMIT 1"));
    }

    private QuizRecord findRecordByAssignment(Long assignmentId, Long studentId) {
        return quizRecordMapper.selectOne(new LambdaQueryWrapper<QuizRecord>()
                .eq(QuizRecord::getAssignmentId, assignmentId)
                .eq(QuizRecord::getStudentId, studentId));
    }

    private Quiz getQuizOrThrow(Long quizId) {
        Quiz quiz = quizMapper.selectById(quizId);
        if (quiz == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "测验不存在");
        }
        return quiz;
    }

    private boolean isFavorited(Long quizId, Long userId) {
        if (quizId == null || userId == null) {
            return false;
        }
        Long count = quizFavoriteMapper.selectCount(new LambdaQueryWrapper<QuizFavorite>()
                .eq(QuizFavorite::getTeacherId, userId)
                .eq(QuizFavorite::getQuizId, quizId));
        return count != null && count > 0;
    }

    private Map<Long, String> loadAuthorNames(List<Quiz> quizzes) {
        List<Long> teacherIds = quizzes.stream()
                .map(Quiz::getTeacherId)
                .filter(Objects::nonNull)
                .distinct()
                .collect(Collectors.toList());
        if (teacherIds.isEmpty()) {
            return new HashMap<>();
        }
        Map<Long, String> map = new HashMap<>();
        for (User u : userMapper.selectBatchIds(teacherIds)) {
            String name = u.getNickname() == null || u.getNickname().trim().isEmpty()
                    ? u.getUsername() : u.getNickname();
            map.put(u.getId(), name);
        }
        return map;
    }

    private String loadUserName(Long userId) {
        if (userId == null) {
            return null;
        }
        User u = userMapper.selectById(userId);
        if (u == null) {
            return null;
        }
        return u.getNickname() == null || u.getNickname().trim().isEmpty() ? u.getUsername() : u.getNickname();
    }

    private Map<Long, Boolean> loadFavorites(List<Long> quizIds, Long userId) {
        Map<Long, Boolean> map = new HashMap<>();
        if (quizIds == null || quizIds.isEmpty()) {
            return map;
        }
        List<QuizFavorite> favorites = quizFavoriteMapper.selectList(new LambdaQueryWrapper<QuizFavorite>()
                .eq(QuizFavorite::getTeacherId, userId)
                .in(QuizFavorite::getQuizId, quizIds));
        for (QuizFavorite f : favorites) {
            map.put(f.getQuizId(), true);
        }
        return map;
    }

    private int calcRemaining(Integer durationMinutes, LocalDateTime endTime, QuizRecord record, LocalDateTime now) {
        long remaining = Long.MAX_VALUE;
        if (durationMinutes != null && durationMinutes > 0) {
            long elapsed = Duration.between(record.getStartAt(), now).getSeconds();
            remaining = durationMinutes * 60L - elapsed;
        }
        if (endTime != null) {
            long untilEnd = Duration.between(now, endTime).getSeconds();
            remaining = Math.min(remaining, untilEnd);
        }
        if (remaining == Long.MAX_VALUE) {
            return -1;
        }
        return (int) Math.max(0, remaining);
    }

    private void notifyStudents(QuizAssignment assignment, Course course) {
        try {
            Notice notice = new Notice();
            notice.setCourseId(assignment.getCourseId());
            notice.setType(Notice.TYPE_QUIZ);
            notice.setTitle("老师在《" + (course == null ? "课程" : course.getName()) + "》发布了新测验");
            notice.setContent(assignment.getTitle());
            notice.setTeacherId(assignment.getTeacherId());
            noticeMapper.insert(notice);
        } catch (Exception ignored) {
        }
    }

    // ==================== VO 组装 ====================

    private List<QuizVO> buildTemplateVOList(List<Quiz> quizzes, Long userId) {
        if (quizzes.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> ids = quizzes.stream().map(Quiz::getId).collect(Collectors.toList());
        Map<Long, Integer> countMap = countQuestions(ids);
        Map<Long, String> authorMap = loadAuthorNames(quizzes);
        Map<Long, Boolean> favoriteMap = loadFavorites(ids, userId);
        List<QuizVO> result = new ArrayList<>();
        for (Quiz quiz : quizzes) {
            QuizVO vo = buildTemplateVO(quiz, userId, favoriteMap.getOrDefault(quiz.getId(), false));
            vo.setQuestionCount(countMap.getOrDefault(quiz.getId(), 0));
            vo.setAuthorName(authorMap.get(quiz.getTeacherId()));
            result.add(vo);
        }
        return result;
    }

    private QuizVO buildTemplateVO(Quiz quiz, Long userId, boolean favorited) {
        QuizVO vo = new QuizVO();
        BeanUtils.copyProperties(quiz, vo);
        vo.setShowAnswerAfter(quiz.getShowAnswerAfter() != null && quiz.getShowAnswerAfter() == 1);
        vo.setStatus(quiz.getStatus());
        vo.setStatusText(quiz.getStatus() == Quiz.STATUS_DRAFT ? "草稿" : "已就绪");
        vo.setQuestionCount(countQuestionsFor(quiz.getId()));
        vo.setVisibility(quiz.getVisibility() == null ? Quiz.VISIBILITY_PRIVATE : quiz.getVisibility());
        vo.setShared(vo.getVisibility() == Quiz.VISIBILITY_PUBLIC);
        vo.setFavorited(favorited);
        vo.setFavoriteCount(quiz.getFavoriteCount() == null ? 0 : quiz.getFavoriteCount());
        vo.setUseCount(quiz.getUseCount() == null ? 0 : quiz.getUseCount());
        vo.setMine(quiz.getTeacherId() != null && quiz.getTeacherId().equals(userId));
        vo.setCanEdit(vo.getMine() && quiz.getStatus() == Quiz.STATUS_DRAFT);
        vo.setCanAssign(vo.getMine());
        vo.setCanEnd(false);
        vo.setCanTake(false);
        vo.setTaken(false);
        if (quiz.getTeacherId() != null) {
            User teacher = userMapper.selectById(quiz.getTeacherId());
            if (teacher != null) {
                vo.setAuthorName(teacher.getNickname() == null || teacher.getNickname().trim().isEmpty()
                        ? teacher.getUsername() : teacher.getNickname());
            }
        }
        return vo;
    }

    private List<QuizVO> buildAssignmentVOList(List<QuizAssignment> assignments, Course course,
                                                Long userId, String role) {
        Map<Long, Course> courseMap = new HashMap<>();
        if (course != null) {
            courseMap.put(course.getId(), course);
        }
        return buildAssignmentVOListMulti(assignments, courseMap, userId, role);
    }

    private List<QuizVO> buildAssignmentVOListMulti(List<QuizAssignment> assignments,
                                                    Map<Long, Course> courseMap,
                                                    Long userId, String role) {
        if (assignments.isEmpty()) {
            return new ArrayList<>();
        }
        List<Long> quizIds = assignments.stream().map(QuizAssignment::getQuizId)
                .distinct().collect(Collectors.toList());
        List<Quiz> quizzes = quizMapper.selectBatchIds(quizIds);
        Map<Long, Quiz> quizMap = quizzes.stream().collect(Collectors.toMap(Quiz::getId, q -> q));
        Map<Long, Integer> countMap = countQuestions(quizIds);
        Map<Long, String> authorMap = loadAuthorNames(quizzes);

        Map<Long, QuizRecord> recordMap = new HashMap<>();
        if (Roles.STUDENT.equals(role)) {
            recordMap = loadMyRecordsByAssignment(
                    assignments.stream().map(QuizAssignment::getId).collect(Collectors.toList()), userId);
        }

        List<QuizVO> result = new ArrayList<>();
        for (QuizAssignment a : assignments) {
            Quiz quiz = quizMap.get(a.getQuizId());
            if (quiz == null) {
                continue;
            }
            result.add(buildAssignmentVO(quiz, a, courseMap.get(a.getCourseId()),
                    recordMap.get(a.getId()), countMap.getOrDefault(quiz.getId(), 0),
                    userId, role, authorMap.get(a.getTeacherId())));
        }
        return result;
    }

    private QuizVO buildAssignmentVO(Quiz quiz, QuizAssignment assignment, Course course,
                                     QuizRecord record, int questionCount,
                                     Long userId, String role, String authorName) {
        QuizVO vo = new QuizVO();
        vo.setId(quiz.getId());
        vo.setAssignmentId(assignment.getId());
        vo.setCourseId(assignment.getCourseId());
        vo.setCourseName(course == null ? null : course.getName());
        vo.setTeacherName(course == null ? null : course.getTeacherName());
        vo.setAuthorName(authorName == null ? loadUserName(assignment.getTeacherId()) : authorName);
        vo.setTitle(assignment.getTitle() == null ? quiz.getTitle() : assignment.getTitle());
        vo.setDescription(quiz.getDescription());
        vo.setFullScore(quiz.getFullScore());
        vo.setDurationMinutes(assignment.getDurationMinutes());
        vo.setStartTime(assignment.getStartTime());
        vo.setEndTime(assignment.getEndTime());
        vo.setShowAnswerAfter(quiz.getShowAnswerAfter() != null && quiz.getShowAnswerAfter() == 1);
        vo.setQuestionCount(questionCount);
        vo.setCreateTime(assignment.getCreateTime());
        vo.setAssignmentStatus(assignment.getStatus());
        vo.setAssignmentStatusText(assignmentStatusText(assignment.getStatus()));
        vo.setStatus(assignment.getStatus());
        vo.setTaken(record != null && record.getStatus() == QuizRecord.STATUS_SUBMITTED);
        vo.setMyScore(record == null ? null : record.getScore());
        vo.setMine(assignment.getTeacherId() != null && assignment.getTeacherId().equals(userId));
        vo.setCanEdit(false);
        vo.setCanEnd(false);
        vo.setCanAssign(false);

        if (Roles.TEACHER.equals(role)) {
            vo.setStatusText(assignmentStatusText(assignment.getStatus()));
            vo.setCanEnd(vo.getMine() && assignment.getStatus() == QuizAssignment.STATUS_OPEN);
            vo.setCanEdit(vo.getMine() && assignment.getStatus() == QuizAssignment.STATUS_PENDING);
            vo.setCanTake(false);
        } else {
            LocalDateTime now = LocalDateTime.now();
            boolean submitted = record != null && record.getStatus() == QuizRecord.STATUS_SUBMITTED;
            boolean ended = assignment.getStatus() == QuizAssignment.STATUS_ENDED
                    || (assignment.getEndTime() != null && now.isAfter(assignment.getEndTime()));
            boolean notStarted = assignment.getStartTime() != null && now.isBefore(assignment.getStartTime());
            if (submitted) {
                vo.setStatusText("已提交");
            } else if (ended) {
                vo.setStatusText("已结束");
            } else if (notStarted) {
                vo.setStatusText("未开始");
            } else {
                vo.setStatusText("进行中");
            }
            vo.setCanTake(!submitted && !ended && !notStarted
                    && assignment.getStatus() == QuizAssignment.STATUS_OPEN);
        }
        return vo;
    }

    private String assignmentStatusText(Integer status) {
        if (status == null) {
            return "未知";
        }
        if (status == QuizAssignment.STATUS_PENDING) {
            return "待发布";
        }
        if (status == QuizAssignment.STATUS_OPEN) {
            return "进行中";
        }
        return "已结束";
    }

    private QuizQuestionVO toQuestionVO(QuizQuestion q, boolean withAnswer, String myAnswer) {
        QuizQuestionVO vo = new QuizQuestionVO();
        BeanUtils.copyProperties(q, vo);
        vo.setOptions(parseOptions(q.getOptions()));
        if (!withAnswer) {
            vo.setCorrectAnswer(null);
            vo.setAnalysis(null);
        }
        vo.setMyAnswer(myAnswer);
        return vo;
    }

    private QuizRecordVO buildRecordVO(Quiz quiz, QuizRecord record, List<QuizQuestion> questions,
                                       List<QuizAnswer> details) {
        Map<Long, QuizAnswer> detailMap = details.stream()
                .collect(Collectors.toMap(QuizAnswer::getQuestionId, a -> a, (a, b) -> a));
        boolean showAnswer = quiz.getShowAnswerAfter() != null && quiz.getShowAnswerAfter() == 1;

        QuizRecordVO vo = new QuizRecordVO();
        vo.setRecordId(record.getId());
        vo.setQuizId(quiz.getId());
        vo.setAssignmentId(record.getAssignmentId());
        vo.setQuizTitle(quiz.getTitle());
        Course course = null;
        if (record.getAssignmentId() != null) {
            QuizAssignment assignment = quizAssignmentMapper.selectById(record.getAssignmentId());
            if (assignment != null) {
                vo.setQuizTitle(assignment.getTitle() == null ? quiz.getTitle() : assignment.getTitle());
                course = courseMapper.selectById(assignment.getCourseId());
            }
        }
        vo.setCourseName(course == null ? null : course.getName());
        vo.setScore(record.getScore());
        vo.setFullScore(quiz.getFullScore());
        vo.setStatus(record.getStatus());
        vo.setStatusText(record.getStatus() == QuizRecord.STATUS_SUBMITTED ? "已提交" : "答题中");
        vo.setSubmitAt(record.getSubmitAt());
        vo.setShowAnswer(showAnswer);

        List<QuizQuestionVO> list = new ArrayList<>();
        for (QuizQuestion q : questions) {
            QuizAnswer detail = detailMap.get(q.getId());
            QuizQuestionVO qvo = toQuestionVO(q, showAnswer, detail == null ? null : detail.getAnswer());
            qvo.setCorrect(detail != null && detail.getIsCorrect() != null && detail.getIsCorrect() == 1);
            list.add(qvo);
        }
        vo.setQuestions(list);
        return vo;
    }

    private boolean judge(QuizQuestion q, String answer) {
        if (answer == null || answer.trim().isEmpty()) {
            return false;
        }
        String standard = q.getCorrectAnswer();
        if (standard == null || standard.trim().isEmpty()) {
            return false;
        }
        int t = q.getType() == null ? QuizQuestion.TYPE_SINGLE : q.getType();
        if (t == QuizQuestion.TYPE_MULTI) {
            Set<String> mine = splitSet(answer);
            Set<String> expect = splitSet(standard);
            return !mine.isEmpty() && mine.equals(expect);
        }
        if (t == QuizQuestion.TYPE_BLANK) {
            for (String s : standard.split("\\|")) {
                String v = s.trim();
                if (!v.isEmpty() && v.equalsIgnoreCase(answer.trim())) {
                    return true;
                }
            }
            return false;
        }
        return standard.trim().equalsIgnoreCase(answer.trim());
    }

    private String normalizeAnswer(Integer type, String raw) {
        if (raw == null) {
            return null;
        }
        String value = raw.trim();
        if (value.isEmpty()) {
            return null;
        }
        int t = type == null ? QuizQuestion.TYPE_SINGLE : type;
        if (t == QuizQuestion.TYPE_MULTI) {
            Set<String> set = splitSet(raw);
            return set.isEmpty() ? null : String.join(",", set);
        }
        return value;
    }

    private Set<String> splitSet(String value) {
        Set<String> set = new LinkedHashSet<>();
        if (value == null) {
            return set;
        }
        for (String s : value.split(",")) {
            String v = s.trim();
            if (!v.isEmpty()) {
                set.add(v);
            }
        }
        return set;
    }

    private String toJson(List<String> options) {
        try {
            return JSON.writeValueAsString(options);
        } catch (Exception e) {
            return "[]";
        }
    }

    private List<String> parseOptions(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return JSON.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private LocalDateTime parseTime(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String v = value.trim().replace('T', ' ');
        if (v.length() > 19) {
            v = v.substring(0, 19);
        }
        try {
            return LocalDateTime.parse(v, DTF);
        } catch (Exception e) {
            try {
                return LocalDateTime.parse(value.trim());
            } catch (Exception ignored) {
                throw new BizException(ErrorCode.BAD_REQUEST, "时间格式错误: " + value);
            }
        }
    }
}