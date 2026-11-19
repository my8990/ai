package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ai.cache.LiveStateCache;
import com.example.ai.cache.LiveStateSnapshot;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.*;
import com.example.ai.entity.*;
import com.example.ai.mapper.*;
import com.example.ai.security.FileUrlSigner;
import com.example.ai.security.Roles;
import com.example.ai.service.CheckinService;
import com.example.ai.service.CourseAccessService;
import com.example.ai.service.CoursewarePublishService;
import com.example.ai.service.LiveService;
import com.example.ai.util.WxApiUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class LiveServiceImpl implements LiveService {

    private static final int CLASS_CHECKIN_TYPE = 3;

    private static final int CLASS_CHECKIN_LATE_MINUTES = 5;

    private static final int ONLINE_WINDOW_SECONDS = 30;

    private static final int JOIN_CODE_MAX_FAILURES = 5;

    private static final long JOIN_CODE_LOCK_MILLIS = 60_000L;

    private static final long WS_TICKET_TTL_MILLIS = 60_000L;

    private static final int PRESENCE_SWEEP_BATCH = 200;

    private static final int PRESENCE_SWEEP_MAX_BATCHES = 5;

    private static final long PUSH_STATE_REUSE_MILLIS = 200L;

    private static final long MEMBER_CACHE_TTL_MILLIS = 5_000L;

    private static final int MEMBER_CACHE_MAX_SIZE = 2048;

    private final LiveSessionMapper liveSessionMapper;
    private final LiveSlideMapper liveSlideMapper;
    private final LivePresenceMapper livePresenceMapper;
    private final LiveInteractionMapper liveInteractionMapper;
    private final LiveWsTicketMapper liveWsTicketMapper;
    private final LiveJoinGuardMapper liveJoinGuardMapper;
    private final CheckinMapper checkinMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final QuizAssignmentMapper quizAssignmentMapper;
    private final QuizMapper quizMapper;
    private final QuizRecordMapper quizRecordMapper;
    private final QuizQuestionMapper quizQuestionMapper;
    private final NoticeMapper noticeMapper;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final UserMapper userMapper;
    private final CourseAccessService courseAccessService;
    private final CoursewarePublishService coursewarePublishService;
    private final CheckinService checkinService;
    private final WxApiUtil wxApiUtil;
    private final LiveStateCache stateCache;
    private final FileUrlSigner fileUrlSigner;
    private final com.example.ai.util.GeocodeUtil geocodeUtil;

    @Value("${wechat.wxacode-page:pages/live/live}")
    private String wxacodePage;

    @Value("${app.live.presence-write-interval-ms:10000}")
    private long presenceWriteIntervalMs;

    private static final Map<Long, String> WXACODE_CACHE = new ConcurrentHashMap<>();

    private final Map<String, Long> presenceWriteAt = new ConcurrentHashMap<>();

    private final Map<String, MemberCacheEntry> memberCache = new ConcurrentHashMap<>();

    private static final class MemberCacheEntry {

        private final Course course;

        private final long expireAt;

        private MemberCacheEntry(Course course, long expireAt) {
            this.course = course;
            this.expireAt = expireAt;
        }
    }

    private Course requireMemberCached(Long courseId, Long userId, String role) {
        String key = courseId + ":" + userId + ":" + role;
        long now = System.currentTimeMillis();
        MemberCacheEntry entry = memberCache.get(key);
        if (entry != null && entry.expireAt > now) {
            return entry.course;
        }
        Course course = courseAccessService.requireMember(courseId, userId, role);
        if (memberCache.size() >= MEMBER_CACHE_MAX_SIZE) {
            memberCache.clear();
        }
        memberCache.put(key, new MemberCacheEntry(course, now + MEMBER_CACHE_TTL_MILLIS));
        return course;
    }

    @Override
    @Transactional
    public LiveSessionVO start(LiveStartDTO dto, Long userId, String role) {
        Course course = courseAccessService.requireTeacher(dto.getCourseId(), userId, role);

        closeActiveSessions(dto.getCourseId());

        String title = dto.getTitle() == null || dto.getTitle().isBlank()
                ? course.getName() + " 课堂"
                : dto.getTitle().trim();
        int expireMinutes = dto.getExpireMinutes() == null ? 0 : dto.getExpireMinutes();
        int lateMinutes = dto.getLateMinutes() == null || dto.getLateMinutes() < 0
                ? CLASS_CHECKIN_LATE_MINUTES
                : dto.getLateMinutes();

        CheckinCreateDTO checkinDTO = new CheckinCreateDTO();
        checkinDTO.setCourseId(dto.getCourseId());
        checkinDTO.setTitle(title + "签到");
        checkinDTO.setType(CLASS_CHECKIN_TYPE);
        checkinDTO.setLateMinutes(lateMinutes);
        checkinDTO.setExpireMinutes(expireMinutes > 0 ? expireMinutes : null);
        CheckinVO checkinVO = checkinService.createCheckin(checkinDTO, userId, role);

        LiveSession session = new LiveSession();
        session.setCourseId(dto.getCourseId());
        session.setTeacherId(userId);
        session.setCheckinId(checkinVO.getId());
        session.setTitle(title);
        session.setCurrentPage(0);
        session.setSlideTotal(0);
        session.setStatus(1);
        insertSessionWithUniqueJoinCode(session);

        return toSessionVO(session, course);
    }

    @Override
    @Transactional
    public void end(Long sessionId, Long userId, String role) {
        end(sessionId, userId, role, false);
    }

    @Override
    @Transactional
    public void end(Long sessionId, Long userId, String role, boolean publishCourseware) {
        LiveSession session = requireSession(sessionId);
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        if (session.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课堂已结束");
        }
        finishSession(session);
        liveInteractionMapper.delete(
                new LambdaQueryWrapper<LiveInteraction>()
                        .eq(LiveInteraction::getSessionId, sessionId)
        );
        if (publishCourseware) {
            publishCoursewareAfterCommit(sessionId, userId, role);
        }
    }

    private void publishCoursewareAfterCommit(Long sessionId, Long userId, String role) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    coursewarePublishService.publishAsync(sessionId, userId, role);
                }
            });
            return;
        }
        coursewarePublishService.publishAsync(sessionId, userId, role);
    }

    private void applyOriginalDocument(LiveSession session, String documentUrl, String fileName) {
        if (documentUrl == null || documentUrl.isBlank()) {
            return;
        }
        session.setOriginalDocUrl(fileUrlSigner.normalize(documentUrl.trim()));
        session.setOriginalFileName(fileName == null || fileName.isBlank() ? null : fileName.trim());
        session.setCoursewareStatus(0);
    }

    @Override
    public LiveStateVO join(LiveJoinDTO dto, Long userId, String role) {
        if (!"student".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有学生才能进入课堂");
        }
        LiveSession session = requireSession(dto.getSessionId());
        if (session.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课堂已结束");
        }
        Course course = courseAccessService.requireMember(session.getCourseId(), userId, role);
        Checkin activeCheckin = resolveClassCheckin(session);
        boolean checked = hasChecked(session.getCheckinId(), userId)
                || (activeCheckin != null && hasChecked(activeCheckin.getId(), userId));
        if (!checked) {
            String code = dto.getCode() == null ? "" : dto.getCode().trim();
            ensureJoinCodeAllowed(session.getId(), userId);
            if (!session.getJoinCode().equals(code)) {
                recordJoinCodeFailure(session.getId(), userId);
                throw new BizException(ErrorCode.BAD_REQUEST, "课堂码错误");
            }
            clearJoinCodeAttempt(session.getId(), userId);
        }
        autoCheckin(activeCheckin, userId, dto);
        touchPresence(session.getId(), userId, role);
        LiveStateVO state = buildStateFresh(session, course);
        state.setMyHandRaised(hasPendingHand(session.getId(), userId));
        enrichMyQuizProgress(state, userId, role);
        return state;
    }

    @Override
    public LiveStateVO getState(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        Course course = requireMemberCached(session.getCourseId(), userId, role);
        if (session.getStatus() == 1) {
            touchPresence(session.getId(), userId, role);
        }
        LiveStateVO state = buildState(session, course);
        if ("student".equals(role)) {
            state.setMyHandRaised(hasPendingHand(session.getId(), userId));
        }
        enrichMyQuizProgress(state, userId, role);
        return state;
    }

    @Override
    public LiveStateVO turnPage(LivePageDTO dto, Long userId, String role) {
        LiveSession session = requireSession(dto.getSessionId());
        Course course = courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        if (session.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课堂已结束");
        }

        int total = session.getSlideTotal() == null ? 0 : session.getSlideTotal();
        int pageNo;
        if (total <= 0) {
            pageNo = 0;
        } else {
            pageNo = Math.max(1, Math.min(dto.getPageNo(), total));
        }
        session.setCurrentPage(pageNo);
        liveSessionMapper.updateById(session);
        return buildStateFresh(session, course);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LiveQuizVO publishQuiz(Long sessionId, LiveQuizPublishDTO dto, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        Course course = courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        if (session.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课堂已结束");
        }
        if (dto == null || dto.getQuizId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "请选择要发布的测验");
        }
        Quiz quiz = quizMapper.selectById(dto.getQuizId());
        if (quiz == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "测验不存在");
        }
        if (quiz.getTeacherId() != null && !quiz.getTeacherId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能发布自己创建的测验");
        }
        Long questionCount = quizQuestionMapper.selectCount(
                new LambdaQueryWrapper<QuizQuestion>().eq(QuizQuestion::getQuizId, quiz.getId()));
        if (questionCount == null || questionCount == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "测验至少需要一道题目");
        }

        closeQuizAssignment(session.getQuizAssignmentId());

        int minutes = dto.getDurationMinutes() == null ? 0 : Math.max(dto.getDurationMinutes(), 0);
        LocalDateTime now = LocalDateTime.now();
        QuizAssignment assignment = new QuizAssignment();
        assignment.setQuizId(quiz.getId());
        assignment.setCourseId(session.getCourseId());
        assignment.setTeacherId(userId);
        assignment.setTitle(dto.getTitle() == null || dto.getTitle().trim().isEmpty()
                ? quiz.getTitle() : dto.getTitle().trim());
        assignment.setDurationMinutes(minutes);
        assignment.setStartTime(now);
        assignment.setEndTime(minutes > 0 ? now.plusMinutes(minutes) : null);
        assignment.setStatus(QuizAssignment.STATUS_OPEN);
        quizAssignmentMapper.insert(assignment);

        if (quiz.getStatus() != null && quiz.getStatus() == Quiz.STATUS_DRAFT) {
            quiz.setStatus(Quiz.STATUS_READY);
            quizMapper.updateById(quiz);
        }

        session.setQuizAssignmentId(assignment.getId());
        liveSessionMapper.updateById(session);
        stateCache.evict(sessionId);

        notifyClassQuiz(course, assignment);
        return buildLiveQuiz(session, assignment, quiz);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void endQuiz(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        if (session.getQuizAssignmentId() == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "当前课堂没有进行中的测验");
        }
        closeQuizAssignment(session.getQuizAssignmentId());
        stateCache.evict(sessionId);
    }

    private void closeQuizAssignment(Long assignmentId) {
        if (assignmentId == null) {
            return;
        }
        QuizAssignment assignment = quizAssignmentMapper.selectById(assignmentId);
        if (assignment == null || assignment.getStatus() != QuizAssignment.STATUS_OPEN) {
            return;
        }
        assignment.setStatus(QuizAssignment.STATUS_ENDED);
        quizAssignmentMapper.updateById(assignment);
    }

    private void notifyClassQuiz(Course course, QuizAssignment assignment) {
        try {
            Notice notice = new Notice();
            notice.setCourseId(assignment.getCourseId());
            notice.setType(Notice.TYPE_QUIZ);
            notice.setTitle("老师在《" + (course == null ? "课程" : course.getName()) + "》课堂发布了随堂测验");
            notice.setContent(assignment.getTitle());
            notice.setTeacherId(assignment.getTeacherId());
            noticeMapper.insert(notice);
        } catch (Exception e) {
            log.debug("随堂测验通知写入失败: {}", e.getMessage());
        }
    }

    private LiveQuizVO buildLiveQuiz(LiveSession session, QuizAssignment assignment, Quiz quiz) {
        LiveQuizVO vo = new LiveQuizVO();
        vo.setAssignmentId(assignment.getId());
        vo.setQuizId(assignment.getQuizId());
        vo.setTitle(assignment.getTitle() == null ? (quiz == null ? "随堂测验" : quiz.getTitle()) : assignment.getTitle());
        vo.setQuestionCount((int) countQuizQuestions(assignment.getQuizId()));
        vo.setFullScore(quiz == null ? null : quiz.getFullScore());
        vo.setDurationMinutes(assignment.getDurationMinutes());
        vo.setStartTime(assignment.getStartTime());
        vo.setEndTime(assignment.getEndTime());
        vo.setStatus(assignment.getStatus());
        vo.setStatusText(quizStatusText(assignment.getStatus()));
        vo.setSubmittedCount((int) countQuizSubmitted(assignment.getId()));
        vo.setTotalCount(getActiveStudentCount(session.getCourseId()));
        return vo;
    }

    private long countQuizQuestions(Long quizId) {
        if (quizId == null) {
            return 0;
        }
        Long count = quizQuestionMapper.selectCount(
                new LambdaQueryWrapper<QuizQuestion>().eq(QuizQuestion::getQuizId, quizId));
        return count == null ? 0 : count;
    }

    private long countQuizSubmitted(Long assignmentId) {
        if (assignmentId == null) {
            return 0;
        }
        Long count = quizRecordMapper.selectCount(
                new LambdaQueryWrapper<QuizRecord>()
                        .eq(QuizRecord::getAssignmentId, assignmentId)
                        .eq(QuizRecord::getStatus, QuizRecord.STATUS_SUBMITTED));
        return count == null ? 0 : count;
    }

    private String quizStatusText(int status) {
        if (status == QuizAssignment.STATUS_OPEN) {
            return "进行中";
        }
        if (status == QuizAssignment.STATUS_ENDED) {
            return "已结束";
        }
        return "待发布";
    }

    private LiveQuizVO copyQuiz(LiveQuizVO source) {
        if (source == null) {
            return null;
        }
        LiveQuizVO vo = new LiveQuizVO();
        vo.setAssignmentId(source.getAssignmentId());
        vo.setQuizId(source.getQuizId());
        vo.setTitle(source.getTitle());
        vo.setQuestionCount(source.getQuestionCount());
        vo.setFullScore(source.getFullScore());
        vo.setDurationMinutes(source.getDurationMinutes());
        vo.setStartTime(source.getStartTime());
        vo.setEndTime(source.getEndTime());
        vo.setStatus(source.getStatus());
        vo.setStatusText(source.getStatusText());
        vo.setSubmittedCount(source.getSubmittedCount());
        vo.setTotalCount(source.getTotalCount());
        vo.setMySubmitted(source.getMySubmitted());
        vo.setMyScore(source.getMyScore());
        return vo;
    }

    private void enrichMyQuizProgress(LiveStateVO state, Long userId, String role) {
        LiveQuizVO quiz = state.getCurrentQuiz();
        if (quiz == null || quiz.getAssignmentId() == null) {
            return;
        }
        if (quiz.getStatus() != null && quiz.getStatus() == QuizAssignment.STATUS_OPEN) {
            quiz.setSubmittedCount((int) countQuizSubmitted(quiz.getAssignmentId()));
        }
        if (!"student".equals(role)) {
            return;
        }
        QuizRecord record = quizRecordMapper.selectOne(
                new LambdaQueryWrapper<QuizRecord>()
                        .eq(QuizRecord::getAssignmentId, quiz.getAssignmentId())
                        .eq(QuizRecord::getStudentId, userId)
                        .last("limit 1")
        );
        boolean submitted = record != null
                && record.getStatus() != null
                && record.getStatus() == QuizRecord.STATUS_SUBMITTED;
        quiz.setMySubmitted(submitted);
        quiz.setMyScore(submitted ? record.getScore() : null);
    }

    private LiveQuizVO loadCurrentQuiz(LiveSession session) {
        Long assignmentId = session.getQuizAssignmentId();
        if (assignmentId == null) {
            return null;
        }
        QuizAssignment assignment = quizAssignmentMapper.selectById(assignmentId);
        if (assignment == null) {
            return null;
        }
        Quiz quiz = quizMapper.selectById(assignment.getQuizId());
        return buildLiveQuiz(session, assignment, quiz);
    }

    @Override
    public List<LiveSlideVO> getSlides(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        courseAccessService.requireMember(session.getCourseId(), userId, role);
        List<LiveSlide> slides = liveSlideMapper.selectList(
                new LambdaQueryWrapper<LiveSlide>()
                        .eq(LiveSlide::getSessionId, sessionId)
                        .orderByAsc(LiveSlide::getPageNo)
        );
        List<LiveSlideVO> result = new ArrayList<>();
        for (LiveSlide slide : slides) {
            result.add(toSlideVO(slide));
        }
        return result;
    }

    @Override
    @Transactional
    public LiveSlideVO addSlide(LiveSlideAddDTO dto, Long userId, String role) {
        LiveSession session = requireSession(dto.getSessionId());
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        if (session.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课堂已结束");
        }

        int total = session.getSlideTotal() == null ? 0 : session.getSlideTotal();
        LiveSlide slide = new LiveSlide();
        slide.setSessionId(session.getId());
        slide.setPageNo(total + 1);
        slide.setImageUrl(fileUrlSigner.normalize(dto.getImageUrl()));
        slide.setFileName(dto.getFileName());
        liveSlideMapper.insert(slide);

        applyOriginalDocument(session, dto.getDocumentUrl(), dto.getFileName());

        session.setSlideTotal(total + 1);
        if (session.getCurrentPage() == null || session.getCurrentPage() <= 0) {
            session.setCurrentPage(1);
        }
        liveSessionMapper.updateById(session);
        stateCache.evict(session.getId());

        return toSlideVO(slide);
    }

    @Override
    @Transactional
    public List<LiveSlideVO> addSlides(LiveSlideBatchDTO dto, Long userId, String role) {
        LiveSession session = requireSession(dto.getSessionId());
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        if (session.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课堂已结束");
        }

        List<String> urls = new ArrayList<>();
        if (dto.getPageUrls() != null) {
            for (String url : dto.getPageUrls()) {
                if (url != null && !url.isBlank()) {
                    urls.add(fileUrlSigner.normalize(url.trim()));
                }
            }
        }
        if (urls.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课件页地址不能为空");
        }

        applyOriginalDocument(session, dto.getDocumentUrl(), dto.getFileName());

        int total = session.getSlideTotal() == null ? 0 : session.getSlideTotal();
        List<LiveSlideVO> result = new ArrayList<>();
        for (int i = 0; i < urls.size(); i++) {
            LiveSlide slide = new LiveSlide();
            slide.setSessionId(session.getId());
            slide.setPageNo(total + i + 1);
            slide.setImageUrl(urls.get(i));
            slide.setFileName(dto.getFileName());
            liveSlideMapper.insert(slide);
            result.add(toSlideVO(slide));
        }

        session.setSlideTotal(total + urls.size());
        if (session.getCurrentPage() == null || session.getCurrentPage() <= 0) {
            session.setCurrentPage(1);
        }
        liveSessionMapper.updateById(session);
        stateCache.evict(session.getId());

        return result;
    }

    @Override
    @Transactional
    public Long deleteSlide(Long slideId, Long userId, String role) {
        LiveSlide slide = liveSlideMapper.selectById(slideId);
        if (slide == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "课件页不存在");
        }
        LiveSession session = requireSession(slide.getSessionId());
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);

        liveSlideMapper.deleteById(slideId);

        List<LiveSlide> remain = liveSlideMapper.selectList(
                new LambdaQueryWrapper<LiveSlide>()
                        .eq(LiveSlide::getSessionId, session.getId())
                        .orderByAsc(LiveSlide::getPageNo)
        );
        for (int i = 0; i < remain.size(); i++) {
            LiveSlide item = remain.get(i);
            if (item.getPageNo() == null || item.getPageNo() != i + 1) {
                item.setPageNo(i + 1);
                liveSlideMapper.updateById(item);
            }
        }

        int total = remain.size();
        session.setSlideTotal(total);
        int current = session.getCurrentPage() == null ? 0 : session.getCurrentPage();
        session.setCurrentPage(total <= 0 ? 0 : Math.min(Math.max(current, 1), total));
        liveSessionMapper.updateById(session);
        stateCache.evict(session.getId());
        return session.getId();
    }

    @Override
    public LiveSessionVO current(Long courseId, Long userId, String role) {
        Course course = courseAccessService.requireMember(courseId, userId, role);
        LiveSession session = liveSessionMapper.selectOne(
                new LambdaQueryWrapper<LiveSession>()
                        .eq(LiveSession::getCourseId, courseId)
                        .eq(LiveSession::getStatus, 1)
                        .orderByDesc(LiveSession::getCreateTime)
                        .last("limit 1")
        );
        if (session == null) {
            return null;
        }
        return toSessionVO(session, course);
    }

    @Override
    public List<LiveSessionVO> listActive(Long userId, String role) {
        List<Long> courseIds = new ArrayList<>();
        if ("student".equals(role)) {
            List<CourseStudent> memberships = courseStudentMapper.selectList(
                    new LambdaQueryWrapper<CourseStudent>()
                            .eq(CourseStudent::getStudentId, userId)
                            .eq(CourseStudent::getStatus, 1)
            );
            for (CourseStudent cs : memberships) {
                courseIds.add(cs.getCourseId());
            }
        } else if ("teacher".equals(role)) {
            List<Course> courses = courseMapper.selectList(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, userId)
            );
            for (Course course : courses) {
                courseIds.add(course.getId());
            }
        }
        if (courseIds.isEmpty()) {
            return new ArrayList<>();
        }
        List<LiveSession> sessions = liveSessionMapper.selectList(
                new LambdaQueryWrapper<LiveSession>()
                        .eq(LiveSession::getStatus, 1)
                        .in(LiveSession::getCourseId, courseIds)
                        .orderByDesc(LiveSession::getCreateTime)
        );
        List<LiveSessionVO> result = new ArrayList<>();
        for (LiveSession session : sessions) {
            Course course = courseMapper.selectById(session.getCourseId());
            if (course == null) {
                continue;
            }
            Checkin activeCheckin = resolveClassCheckin(session);
            LiveSessionVO vo = toSessionVO(session, course, activeCheckin);
            if ("student".equals(role)) {
                vo.setChecked(hasChecked(session.getCheckinId(), userId)
                        || (activeCheckin != null && hasChecked(activeCheckin.getId(), userId)));
            }
            result.add(vo);
        }
        return result;
    }

    private void closeActiveSessions(Long courseId) {
        List<LiveSession> actives = liveSessionMapper.selectList(
                new LambdaQueryWrapper<LiveSession>()
                        .eq(LiveSession::getCourseId, courseId)
                        .eq(LiveSession::getStatus, 1)
        );
        for (LiveSession old : actives) {
            finishSession(old);
        }
    }

    private void finishSession(LiveSession session) {
        Checkin activeCheckin = resolveClassCheckin(session);
        closeQuizAssignment(session.getQuizAssignmentId());
        session.setStatus(0);
        session.setEndTime(LocalDateTime.now());
        session.setEnterCount(countPresence(session.getId()));
        liveSessionMapper.updateById(session);
        closeCheckin(session.getCheckinId());
        if (activeCheckin != null && !activeCheckin.getId().equals(session.getCheckinId())) {
            closeCheckin(activeCheckin.getId());
        }
        clearPresence(session.getId());
        stateCache.evict(session.getId());
    }

    private Checkin resolveClassCheckin(LiveSession session) {
        Checkin bound = session.getCheckinId() == null ? null : checkinMapper.selectById(session.getCheckinId());
        if (isCheckinUsable(bound)) {
            return bound;
        }
        List<Checkin> actives = checkinMapper.selectList(
                new LambdaQueryWrapper<Checkin>()
                        .eq(Checkin::getCourseId, session.getCourseId())
                        .eq(Checkin::getType, CLASS_CHECKIN_TYPE)
                        .eq(Checkin::getStatus, 1)
                        .orderByDesc(Checkin::getId)
        );
        for (Checkin item : actives) {
            if (isCheckinUsable(item)) {
                return item;
            }
        }
        return null;
    }

    private boolean isCheckinUsable(Checkin checkin) {
        if (checkin == null || checkin.getStatus() == null || checkin.getStatus() != 1) {
            return false;
        }
        return checkin.getExpireTime() == null || !LocalDateTime.now().isAfter(checkin.getExpireTime());
    }

    private String visibleCheckinCode(Checkin checkin) {
        if (checkin == null || checkin.getType() == null) {
            return null;
        }
        return checkin.getType() == 2 ? null : checkin.getCode();
    }

    private void closeCheckin(Long checkinId) {
        if (checkinId == null) {
            return;
        }
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin != null && checkin.getStatus() == 1) {
            checkin.setStatus(0);
            checkinMapper.updateById(checkin);
        }
    }

    private void autoCheckin(Checkin checkin, Long studentId, LiveJoinDTO dto) {
        if (checkin == null) {
            return;
        }
        if (checkin.getExpireTime() != null && LocalDateTime.now().isAfter(checkin.getExpireTime())) {
            return;
        }

        CheckinRecord existing = checkinRecordMapper.selectOne(
                new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getCheckinId, checkin.getId())
                        .eq(CheckinRecord::getStudentId, studentId)
        );
        if (existing != null) {
            return;
        }

        CheckinRecord record = new CheckinRecord();
        record.setCheckinId(checkin.getId());
        record.setStudentId(studentId);
        record.setStatus(resolveRecordStatus(checkin));
        record.setLatitude(dto.getLatitude());
        record.setLongitude(dto.getLongitude());
        record.setAddress(geocodeUtil.ensureAddress(dto.getAddress(), dto.getLatitude(), dto.getLongitude()));
        checkinRecordMapper.insert(record);
    }

    private int resolveRecordStatus(Checkin checkin) {
        int lateMinutes = checkin.getLateMinutes() == null ? 0 : checkin.getLateMinutes();
        if (lateMinutes <= 0 || checkin.getCreateTime() == null) {
            return 1;
        }
        LocalDateTime lateLine = checkin.getCreateTime().plusMinutes(lateMinutes);
        return LocalDateTime.now().isAfter(lateLine) ? 2 : 1;
    }

    private LiveSession requireSession(Long sessionId) {
        if (sessionId == null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课堂ID不能为空");
        }
        LiveSession session = liveSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "课堂不存在");
        }
        return session;
    }

    @Override
    public String getWxacode(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);

        String cached = WXACODE_CACHE.get(session.getId());
        if (isValidCodeUrl(cached)) {
            return cached;
        }
        WXACODE_CACHE.remove(session.getId());

        String scene = session.getId() + "-" + session.getJoinCode();
        String dataUrl;
        try {
            dataUrl = wxApiUtil.getUnlimitedCode(scene, wxacodePage, 280);
        } catch (BizException e) {
            log.warn("生成课堂小程序码失败: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.warn("生成课堂小程序码失败: {}", e.getMessage());
            throw new BizException(ErrorCode.INTERNAL_ERROR, "小程序码生成失败: " + e.getMessage());
        }
        WXACODE_CACHE.put(session.getId(), dataUrl);
        return dataUrl;
    }

    private boolean isValidCodeUrl(String dataUrl) {
        return dataUrl != null
                && dataUrl.startsWith("data:image/")
                && dataUrl.length() > 2048;
    }

    private String generateJoinCode() {
        SecureRandom random = new SecureRandom();
        for (int attempt = 0; attempt < 20; attempt++) {
            StringBuilder sb = new StringBuilder(6);
            for (int i = 0; i < 6; i++) {
                sb.append(random.nextInt(10));
            }
            String code = sb.toString();
            Long count = liveSessionMapper.selectCount(
                    new LambdaQueryWrapper<LiveSession>()
                            .eq(LiveSession::getJoinCode, code)
                            .eq(LiveSession::getStatus, 1)
            );
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR, "课堂码生成失败，请重试");
    }

    private void insertSessionWithUniqueJoinCode(LiveSession session) {
        for (int attempt = 1; attempt <= 20; attempt++) {
            session.setJoinCode(generateJoinCode());
            try {
                liveSessionMapper.insert(session);
                return;
            } catch (DuplicateKeyException e) {
                session.setId(null);
                log.warn("课堂码冲突，重新生成: attempt={}, code={}", attempt, session.getJoinCode());
            }
        }
        throw new BizException(ErrorCode.INTERNAL_ERROR, "课堂码生成失败，请重试");
    }

    @Override
    public void heartbeat(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        requireMemberCached(session.getCourseId(), userId, role);
        if (session.getStatus() == 0) {
            return;
        }
        touchPresence(session.getId(), userId, role);
    }

    @Override
    public List<LiveStudentVO> onlineStudents(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        return buildStudentList(session, LocalDateTime.now().minusSeconds(ONLINE_WINDOW_SECONDS));
    }

    @Override
    public List<LiveSessionVO> history(Long courseId, Long userId, String role) {
        Course course = courseAccessService.requireMember(courseId, userId, role);
        List<LiveSession> sessions = liveSessionMapper.selectList(
                new LambdaQueryWrapper<LiveSession>()
                        .eq(LiveSession::getCourseId, courseId)
                        .eq(LiveSession::getStatus, 0)
                        .orderByDesc(LiveSession::getCreateTime)
                        .last("limit 30")
        );
        List<LiveSessionVO> result = new ArrayList<>();
        int totalCount = getActiveStudentCount(courseId);
        for (LiveSession session : sessions) {
            LiveSessionVO vo = toSessionVO(session, course, null);
            vo.setTotalCount(totalCount);
            if ("student".equals(role)) {
                vo.setChecked(hasChecked(session.getCheckinId(), userId));
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    public LiveReportVO report(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        Course course = courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        List<LiveStudentVO> students = buildStudentList(session, LocalDateTime.now().minusSeconds(ONLINE_WINDOW_SECONDS));

        int checked = 0;
        int late = 0;
        for (LiveStudentVO student : students) {
            if (Boolean.TRUE.equals(student.getChecked())) {
                checked++;
            }
            if (Boolean.TRUE.equals(student.getLate())) {
                late++;
            }
        }

        LiveReportVO vo = new LiveReportVO();
        vo.setSessionId(session.getId());
        vo.setCourseId(session.getCourseId());
        vo.setCourseName(course.getName());
        vo.setTitle(session.getTitle());
        vo.setTeacherName(course.getTeacherName());
        vo.setJoinCode(session.getJoinCode());
        vo.setStartTime(session.getCreateTime());
        vo.setEndTime(session.getEndTime());
        vo.setDurationMinutes(durationMinutes(session.getCreateTime(), session.getEndTime()));
        vo.setSlideTotal(session.getSlideTotal() == null ? 0 : session.getSlideTotal());
        vo.setTotalCount(students.size());
        vo.setCheckedCount(checked);
        vo.setLateCount(late);
        vo.setAbsentCount(students.size() - checked);
        int enterCount = session.getEnterCount() == null ? 0 : session.getEnterCount();
        if (enterCount <= 0) {
            enterCount = countPresence(session.getId());
        }
        vo.setOnlineCount(enterCount);
        vo.setStudents(students);
        return vo;
    }

    @Override
    public LiveStateVO getPushState(Long sessionId) {
        LiveSession session = requireSession(sessionId);
        Course course = courseMapper.selectById(session.getCourseId());
        if (course == null) {
            return null;
        }
        LiveStateVO state = buildState(session, course);
        enrichMyQuizProgress(state, null, "teacher");
        return state;
    }

    @Override
    public LiveStateVO refreshPushState(Long sessionId) {
        LiveSession session = requireSession(sessionId);
        Course course = courseMapper.selectById(session.getCourseId());
        if (course == null) {
            return null;
        }
        LiveStateSnapshot cached = stateCache.get(sessionId);
        if (cached != null && System.currentTimeMillis() - cached.getUpdatedAt() <= PUSH_STATE_REUSE_MILLIS) {
            LiveStateVO state = assembleState(session, course, cached);
            enrichMyQuizProgress(state, null, "teacher");
            return state;
        }
        LiveStateVO state = buildStateFresh(session, course);
        enrichMyQuizProgress(state, null, "teacher");
        return state;
    }

    @Override
    public void evictStateCache(Long sessionId) {
        stateCache.evict(sessionId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean toggleHand(Long sessionId, Long userId, String role) {
        if (!"student".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有学生可以举手");
        }
        LiveSession session = requireSession(sessionId);
        if (session.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课堂已结束");
        }
        courseAccessService.requireMember(session.getCourseId(), userId, role);
        LiveInteraction pending = liveInteractionMapper.selectOne(
                new LambdaQueryWrapper<LiveInteraction>()
                        .eq(LiveInteraction::getSessionId, sessionId)
                        .eq(LiveInteraction::getUserId, userId)
                        .eq(LiveInteraction::getType, LiveInteraction.TYPE_HAND)
                        .eq(LiveInteraction::getStatus, LiveInteraction.STATUS_PENDING)
                        .last("limit 1")
        );
        if (pending != null) {
            liveInteractionMapper.deleteById(pending.getId());
            stateCache.evict(sessionId);
            return false;
        }
        LiveInteraction item = new LiveInteraction();
        item.setSessionId(sessionId);
        item.setUserId(userId);
        item.setType(LiveInteraction.TYPE_HAND);
        item.setStatus(LiveInteraction.STATUS_PENDING);
        liveInteractionMapper.insert(item);
        stateCache.evict(sessionId);
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long askQuestion(LiveQuestionDTO dto, Long userId, String role) {
        if (!"student".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有学生可以提问");
        }
        LiveSession session = requireSession(dto.getSessionId());
        if (session.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课堂已结束");
        }
        courseAccessService.requireMember(session.getCourseId(), userId, role);
        String content = dto.getContent() == null ? "" : dto.getContent().trim();
        if (content.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "提问内容不能为空");
        }
        if (content.length() > 200) {
            content = content.substring(0, 200);
        }
        Long pendingCount = liveInteractionMapper.selectCount(
                new LambdaQueryWrapper<LiveInteraction>()
                        .eq(LiveInteraction::getSessionId, session.getId())
                        .eq(LiveInteraction::getUserId, userId)
                        .eq(LiveInteraction::getType, LiveInteraction.TYPE_QUESTION)
                        .eq(LiveInteraction::getStatus, LiveInteraction.STATUS_PENDING)
        );
        if (pendingCount != null && pendingCount >= 3) {
            throw new BizException(ErrorCode.BAD_REQUEST, "你有多个提问还在等待老师处理，请稍后再发");
        }
        LiveInteraction item = new LiveInteraction();
        item.setSessionId(session.getId());
        item.setUserId(userId);
        item.setType(LiveInteraction.TYPE_QUESTION);
        item.setContent(content);
        item.setStatus(LiveInteraction.STATUS_PENDING);
        liveInteractionMapper.insert(item);
        stateCache.evict(session.getId());
        return item.getId();
    }

    @Override
    public List<LiveInteractionVO> interactions(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        List<LiveInteraction> list = liveInteractionMapper.selectList(
                new LambdaQueryWrapper<LiveInteraction>()
                        .eq(LiveInteraction::getSessionId, sessionId)
                        .eq(LiveInteraction::getStatus, LiveInteraction.STATUS_PENDING)
                        .orderByAsc(LiveInteraction::getCreateTime)
                        .orderByAsc(LiveInteraction::getId)
        );
        if (list.isEmpty()) {
            return new ArrayList<>();
        }
        List<LiveInteractionVO> result = new ArrayList<>(list.size());
        Map<Long, User> userMap = loadUsers(list);
        for (LiveInteraction item : list) {
            LiveInteractionVO vo = new LiveInteractionVO();
            vo.setId(item.getId());
            vo.setSessionId(item.getSessionId());
            vo.setUserId(item.getUserId());
            vo.setType(item.getType());
            vo.setContent(item.getContent());
            vo.setStatus(item.getStatus());
            vo.setCreateTime(item.getCreateTime());
            vo.setHandleTime(item.getHandleTime());
            User student = userMap.get(item.getUserId());
            if (student != null) {
                vo.setName(displayName(student));
                vo.setStudentNo(student.getStudentNo());
            }
            result.add(vo);
        }
        return result;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long resolveInteraction(Long id, Long userId, String role) {
        LiveInteraction item = liveInteractionMapper.selectById(id);
        if (item == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "互动记录不存在");
        }
        LiveSession session = requireSession(item.getSessionId());
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        if (LiveInteraction.TYPE_HAND.equals(item.getType())) {
            liveInteractionMapper.deleteById(id);
            stateCache.evict(session.getId());
            return session.getId();
        }
        item.setStatus(LiveInteraction.STATUS_RESOLVED);
        item.setHandleTime(LocalDateTime.now());
        liveInteractionMapper.updateById(item);
        stateCache.evict(session.getId());
        return session.getId();
    }

    private int countPendingInteractions(Long sessionId, String type) {
        Long count = liveInteractionMapper.selectCount(
                new LambdaQueryWrapper<LiveInteraction>()
                        .eq(LiveInteraction::getSessionId, sessionId)
                        .eq(LiveInteraction::getType, type)
                        .eq(LiveInteraction::getStatus, LiveInteraction.STATUS_PENDING)
        );
        return count == null ? 0 : count.intValue();
    }

    @Override
    public Set<Long> pendingHandUserIds(Long sessionId) {
        Set<Long> result = new HashSet<>();
        if (sessionId == null) {
            return result;
        }
        List<Object> ids = liveInteractionMapper.selectObjs(
                new LambdaQueryWrapper<LiveInteraction>()
                        .select(LiveInteraction::getUserId)
                        .eq(LiveInteraction::getSessionId, sessionId)
                        .eq(LiveInteraction::getType, LiveInteraction.TYPE_HAND)
                        .eq(LiveInteraction::getStatus, LiveInteraction.STATUS_PENDING)
                        .groupBy(LiveInteraction::getUserId)
        );
        if (ids == null) {
            return result;
        }
        for (Object id : ids) {
            if (id instanceof Number number) {
                result.add(number.longValue());
            }
        }
        return result;
    }

    private boolean hasPendingHand(Long sessionId, Long userId) {
        Long count = liveInteractionMapper.selectCount(
                new LambdaQueryWrapper<LiveInteraction>()
                        .eq(LiveInteraction::getSessionId, sessionId)
                        .eq(LiveInteraction::getUserId, userId)
                        .eq(LiveInteraction::getType, LiveInteraction.TYPE_HAND)
                        .eq(LiveInteraction::getStatus, LiveInteraction.STATUS_PENDING)
        );
        return count != null && count > 0;
    }

    private Map<Long, User> loadUsers(List<LiveInteraction> list) {
        List<Long> userIds = new ArrayList<>();
        for (LiveInteraction item : list) {
            if (item.getUserId() != null && !userIds.contains(item.getUserId())) {
                userIds.add(item.getUserId());
            }
        }
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        List<User> users = userMapper.selectBatchIds(userIds);
        Map<Long, User> map = new HashMap<>();
        for (User user : users) {
            map.put(user.getId(), user);
        }
        return map;
    }

    @Override
    public LiveWsTicketVO createWsTicket(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        courseAccessService.requireMember(session.getCourseId(), userId, role);
        String ticket = UUID.randomUUID().toString().replace("-", "");
        LiveWsTicket row = new LiveWsTicket();
        row.setTicket(ticket);
        row.setSessionId(sessionId);
        row.setUserId(userId);
        row.setRole(role);
        row.setExpireTime(LocalDateTime.now().plusNanos(WS_TICKET_TTL_MILLIS * 1_000_000L));
        liveWsTicketMapper.insert(row);
        LiveWsTicketVO vo = new LiveWsTicketVO();
        vo.setTicket(ticket);
        vo.setExpireSeconds((int) (WS_TICKET_TTL_MILLIS / 1000));
        return vo;
    }

    @Override
    public WsPrincipal consumeWsTicket(String ticket) {
        if (ticket == null || ticket.isBlank()) {
            return null;
        }
        String key = ticket.trim();
        LiveWsTicket row = liveWsTicketMapper.selectOne(
                new LambdaQueryWrapper<LiveWsTicket>()
                        .eq(LiveWsTicket::getTicket, key)
                        .gt(LiveWsTicket::getExpireTime, LocalDateTime.now())
                        .last("limit 1")
        );
        if (row == null) {
            return null;
        }
        int deleted = liveWsTicketMapper.delete(
                new LambdaQueryWrapper<LiveWsTicket>()
                        .eq(LiveWsTicket::getTicket, key)
                        .gt(LiveWsTicket::getExpireTime, LocalDateTime.now())
        );
        if (deleted <= 0) {
            return null;
        }
        return new WsPrincipal(row.getSessionId(), row.getUserId(), row.getRole());
    }

    @Scheduled(fixedDelayString = "${app.live.ws-ticket-sweep-interval-ms:60000}")
    public void sweepWsTickets() {
        try {
            liveWsTicketMapper.delete(
                    new LambdaQueryWrapper<LiveWsTicket>()
                            .lt(LiveWsTicket::getExpireTime, LocalDateTime.now())
            );
        } catch (Exception e) {
            log.warn("清理过期 WebSocket 凭证失败: {}", e.getMessage());
        }
    }

    private void touchPresence(Long sessionId, Long userId, String role) {
        if (sessionId == null || userId == null) {
            return;
        }
        String key = sessionId + ":" + userId;
        long now = System.currentTimeMillis();
        Long last = presenceWriteAt.get(key);
        if (last != null && presenceWriteIntervalMs > 0 && now - last < presenceWriteIntervalMs) {
            return;
        }
        presenceWriteAt.put(key, now);

        LocalDateTime stamp = LocalDateTime.now();
        int updated = livePresenceMapper.update(null,
                new LambdaUpdateWrapper<LivePresence>()
                        .eq(LivePresence::getSessionId, sessionId)
                        .eq(LivePresence::getUserId, userId)
                        .set(LivePresence::getLastHeartbeat, stamp)
                        .set(role != null, LivePresence::getRole, role));
        if (updated > 0) {
            return;
        }
        LivePresence presence = new LivePresence();
        presence.setSessionId(sessionId);
        presence.setUserId(userId);
        presence.setRole(role == null ? "student" : role);
        presence.setLastHeartbeat(stamp);
        try {
            livePresenceMapper.insert(presence);
        } catch (Exception e) {
            presence.setId(null);
            livePresenceMapper.update(presence,
                    new LambdaQueryWrapper<LivePresence>()
                            .eq(LivePresence::getSessionId, sessionId)
                            .eq(LivePresence::getUserId, userId));
        }
    }

    private int countOnline(Long sessionId) {
        Long count = livePresenceMapper.selectCount(
                new LambdaQueryWrapper<LivePresence>()
                        .eq(LivePresence::getSessionId, sessionId)
                        .eq(LivePresence::getRole, "student")
                        .ge(LivePresence::getLastHeartbeat, LocalDateTime.now().minusSeconds(ONLINE_WINDOW_SECONDS))
        );
        return count == null ? 0 : count.intValue();
    }

    private int countPresence(Long sessionId) {
        Long count = livePresenceMapper.selectCount(
                new LambdaQueryWrapper<LivePresence>()
                        .eq(LivePresence::getSessionId, sessionId)
                        .eq(LivePresence::getRole, "student")
        );
        return count == null ? 0 : count.intValue();
    }

    private void clearPresence(Long sessionId) {
        if (sessionId == null) {
            return;
        }
        livePresenceMapper.delete(
                new LambdaQueryWrapper<LivePresence>()
                        .eq(LivePresence::getSessionId, sessionId)
        );
        String prefix = sessionId + ":";
        presenceWriteAt.keySet().removeIf(key -> key.startsWith(prefix));
        stateCache.evict(sessionId);
    }

    @Scheduled(fixedDelayString = "${app.live.presence-sweep-interval-ms:3600000}")
    public void sweepPresence() {
        LocalDateTime threshold = LocalDateTime.now().minusDays(3);
        long cursor = 0L;
        int cleaned = 0;
        for (int batch = 0; batch < PRESENCE_SWEEP_MAX_BATCHES; batch++) {
            List<LiveSession> ended = liveSessionMapper.selectList(
                    new LambdaQueryWrapper<LiveSession>()
                            .eq(LiveSession::getStatus, 0)
                            .gt(LiveSession::getId, cursor)
                            .and(w -> w.lt(LiveSession::getEndTime, threshold)
                                    .or()
                                    .isNull(LiveSession::getEndTime)
                                    .lt(LiveSession::getCreateTime, threshold))
                            .orderByAsc(LiveSession::getId)
                            .last("limit " + PRESENCE_SWEEP_BATCH)
            );
            if (ended.isEmpty()) {
                break;
            }
            for (LiveSession session : ended) {
                cursor = session.getId();
                Long remaining = livePresenceMapper.selectCount(
                        new LambdaQueryWrapper<LivePresence>()
                                .eq(LivePresence::getSessionId, session.getId())
                );
                liveInteractionMapper.delete(
                        new LambdaQueryWrapper<LiveInteraction>()
                                .eq(LiveInteraction::getSessionId, session.getId())
                );
                stateCache.evict(session.getId());
                if (remaining == null || remaining == 0) {
                    continue;
                }
                if (session.getEnterCount() == null || session.getEnterCount() == 0) {
                    session.setEnterCount(countPresence(session.getId()));
                    liveSessionMapper.updateById(session);
                }
                clearPresence(session.getId());
                cleaned++;
            }
            if (ended.size() < PRESENCE_SWEEP_BATCH) {
                break;
            }
        }
        if (cleaned > 0) {
            log.info("课堂在线状态清理完成: 清理课堂数={}", cleaned);
        }
    }

    private void ensureJoinCodeAllowed(Long sessionId, Long userId) {
        LiveJoinGuard guard = liveJoinGuardMapper.selectOne(
                new LambdaQueryWrapper<LiveJoinGuard>()
                        .eq(LiveJoinGuard::getSessionId, sessionId)
                        .eq(LiveJoinGuard::getUserId, userId)
                        .last("limit 1")
        );
        if (guard == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        if (guard.getLockUntil() != null && guard.getLockUntil().isAfter(now)) {
            long remainSeconds = Duration.between(now, guard.getLockUntil()).getSeconds() + 1;
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "课堂码错误次数过多，请 " + remainSeconds + " 秒后重试");
        }
        if (guard.getLockUntil() != null) {
            guard.setLockUntil(null);
            guard.setFailures(0);
            liveJoinGuardMapper.updateById(guard);
        }
    }

    private void recordJoinCodeFailure(Long sessionId, Long userId) {
        int updated = liveJoinGuardMapper.update(null,
                new LambdaUpdateWrapper<LiveJoinGuard>()
                        .eq(LiveJoinGuard::getSessionId, sessionId)
                        .eq(LiveJoinGuard::getUserId, userId)
                        .setSql("failures = failures + 1"));
        if (updated <= 0) {
            LiveJoinGuard guard = new LiveJoinGuard();
            guard.setSessionId(sessionId);
            guard.setUserId(userId);
            guard.setFailures(1);
            try {
                liveJoinGuardMapper.insert(guard);
            } catch (Exception e) {
                liveJoinGuardMapper.update(null,
                        new LambdaUpdateWrapper<LiveJoinGuard>()
                                .eq(LiveJoinGuard::getSessionId, sessionId)
                                .eq(LiveJoinGuard::getUserId, userId)
                                .setSql("failures = failures + 1"));
            }
            return;
        }
        LiveJoinGuard guard = liveJoinGuardMapper.selectOne(
                new LambdaQueryWrapper<LiveJoinGuard>()
                        .eq(LiveJoinGuard::getSessionId, sessionId)
                        .eq(LiveJoinGuard::getUserId, userId)
                        .last("limit 1")
        );
        if (guard == null) {
            return;
        }
        int failures = guard.getFailures() == null ? 0 : guard.getFailures();
        if (failures >= JOIN_CODE_MAX_FAILURES) {
            guard.setFailures(0);
            guard.setLockUntil(LocalDateTime.now().plusNanos(JOIN_CODE_LOCK_MILLIS * 1_000_000L));
            liveJoinGuardMapper.updateById(guard);
        }
    }

    private void clearJoinCodeAttempt(Long sessionId, Long userId) {
        liveJoinGuardMapper.delete(
                new LambdaQueryWrapper<LiveJoinGuard>()
                        .eq(LiveJoinGuard::getSessionId, sessionId)
                        .eq(LiveJoinGuard::getUserId, userId)
        );
    }

    @Scheduled(fixedDelayString = "${app.live.join-guard-sweep-interval-ms:3600000}")
    public void sweepJoinGuards() {
        try {
            liveJoinGuardMapper.delete(
                    new LambdaQueryWrapper<LiveJoinGuard>()
                            .lt(LiveJoinGuard::getUpdateTime, LocalDateTime.now().minusDays(1))
            );
        } catch (Exception e) {
            log.warn("清理课堂码限流记录失败: {}", e.getMessage());
        }
    }

    private List<Long> classCheckinIds(LiveSession session) {
        return classCheckinIds(session, resolveClassCheckin(session));
    }

    private List<Long> classCheckinIds(LiveSession session, Checkin activeCheckin) {
        List<Long> ids = new ArrayList<>();
        if (session.getCheckinId() != null) {
            ids.add(session.getCheckinId());
        }
        if (activeCheckin != null && !ids.contains(activeCheckin.getId())) {
            ids.add(activeCheckin.getId());
        }
        return ids;
    }

    private List<LiveStudentVO> buildStudentList(LiveSession session, LocalDateTime onlineSince) {
        List<CourseStudent> members = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, session.getCourseId())
                        .eq(CourseStudent::getStatus, 1)
        );
        List<Long> studentIds = new ArrayList<>();
        for (CourseStudent member : members) {
            studentIds.add(member.getStudentId());
        }
        if (studentIds.isEmpty()) {
            return new ArrayList<>();
        }

        Map<Long, User> userMap = new HashMap<>();
        List<User> users = userMapper.selectBatchIds(studentIds);
        for (User user : users) {
            userMap.put(user.getId(), user);
        }

        Map<Long, LocalDateTime> onlineMap = new HashMap<>();
        List<LivePresence> presences = livePresenceMapper.selectList(
                new LambdaQueryWrapper<LivePresence>()
                        .eq(LivePresence::getSessionId, session.getId())
                        .eq(LivePresence::getRole, "student")
                        .ge(LivePresence::getLastHeartbeat, onlineSince)
        );
        for (LivePresence presence : presences) {
            onlineMap.put(presence.getUserId(), presence.getLastHeartbeat());
        }

        Map<Long, CheckinRecord> recordMap = new HashMap<>();
        List<Long> checkinIds = classCheckinIds(session);
        if (!checkinIds.isEmpty()) {
            List<CheckinRecord> records = checkinRecordMapper.selectList(
                    new LambdaQueryWrapper<CheckinRecord>()
                            .in(CheckinRecord::getCheckinId, checkinIds)
                            .in(CheckinRecord::getStatus, 1, 2)
            );
            for (CheckinRecord record : records) {
                CheckinRecord previous = recordMap.get(record.getStudentId());
                if (previous == null
                        || (record.getCreateTime() != null
                        && (previous.getCreateTime() == null || record.getCreateTime().isAfter(previous.getCreateTime())))) {
                    recordMap.put(record.getStudentId(), record);
                }
            }
        }

        List<LiveStudentVO> result = new ArrayList<>();
        for (Long studentId : studentIds) {
            User user = userMap.get(studentId);
            LiveStudentVO vo = new LiveStudentVO();
            vo.setUserId(studentId);
            String name = user == null ? null : displayName(user);
            vo.setName(name == null ? "学生" + studentId : name);
            vo.setStudentNo(user == null ? null : user.getStudentNo());
            LocalDateTime heartbeatTime = onlineMap.get(studentId);
            vo.setOnline(heartbeatTime != null);
            vo.setLastHeartbeat(heartbeatTime);
            CheckinRecord record = recordMap.get(studentId);
            boolean checked = record != null;
            vo.setChecked(checked);
            vo.setLate(checked && record.getStatus() != null && record.getStatus() == 2);
            vo.setCheckinTime(checked ? record.getCreateTime() : null);
            result.add(vo);
        }
        result.sort((a, b) -> {
            if (!a.getOnline().equals(b.getOnline())) {
                return a.getOnline() ? -1 : 1;
            }
            if (!a.getChecked().equals(b.getChecked())) {
                return a.getChecked() ? 1 : -1;
            }
            return a.getName().compareTo(b.getName());
        });
        return result;
    }

    private String displayName(User user) {
        if (user.getNickname() != null && !user.getNickname().isBlank()) {
            return user.getNickname();
        }
        return user.getUsername();
    }

    private Long durationMinutes(LocalDateTime start, LocalDateTime end) {
        if (start == null) {
            return 0L;
        }
        LocalDateTime finish = end == null ? LocalDateTime.now() : end;
        long minutes = Duration.between(start, finish).toMinutes();
        return minutes < 0 ? 0L : minutes;
    }

    private LiveSessionVO toSessionVO(LiveSession session, Course course) {
        return toSessionVO(session, course, resolveClassCheckin(session));
    }

    private LiveSessionVO toSessionVO(LiveSession session, Course course, Checkin activeCheckin) {
        LiveSessionVO vo = new LiveSessionVO();
        vo.setId(session.getId());
        vo.setCourseId(session.getCourseId());
        vo.setCourseName(course.getName());
        vo.setTeacherId(session.getTeacherId());
        vo.setTeacherName(course.getTeacherName());
        if (activeCheckin != null) {
            vo.setCheckinId(activeCheckin.getId());
            vo.setCheckinCode(visibleCheckinCode(activeCheckin));
        } else {
            vo.setCheckinId(session.getCheckinId());
            vo.setCheckinCode(getCheckinCode(session.getCheckinId()));
        }
        vo.setJoinCode(session.getJoinCode());
        vo.setTitle(session.getTitle());
        vo.setCurrentPage(session.getCurrentPage() == null ? 0 : session.getCurrentPage());
        vo.setSlideTotal(session.getSlideTotal() == null ? 0 : session.getSlideTotal());
        vo.setStatus(session.getStatus());
        vo.setCheckedCount(getCheckedCount(session, activeCheckin));
        vo.setTotalCount(getActiveStudentCount(session.getCourseId()));
        vo.setQrContent("live://" + session.getId() + "?code=" + session.getJoinCode());
        vo.setCreateTime(session.getCreateTime());
        vo.setEndTime(session.getEndTime());
        vo.setDurationMinutes(durationMinutes(session.getCreateTime(), session.getEndTime()));
        return vo;
    }

    private LiveStateVO buildState(LiveSession session, Course course) {
        LiveStateSnapshot snapshot = stateCache.get(session.getId());
        if (snapshot == null) {
            snapshot = loadSnapshot(session);
            stateCache.put(session.getId(), snapshot);
        }
        return assembleState(session, course, snapshot);
    }

    private LiveStateVO buildStateFresh(LiveSession session, Course course) {
        LiveStateSnapshot snapshot = loadSnapshot(session);
        stateCache.put(session.getId(), snapshot);
        return assembleState(session, course, snapshot);
    }

    private LiveStateSnapshot loadSnapshot(LiveSession session) {
        LiveStateSnapshot snapshot = new LiveStateSnapshot();
        int currentPage = session.getCurrentPage() == null ? 0 : session.getCurrentPage();
        snapshot.setCurrentPage(currentPage);
        snapshot.setSlideTotal(session.getSlideTotal() == null ? 0 : session.getSlideTotal());
        Checkin activeCheckin = resolveClassCheckin(session);
        snapshot.setCheckinId(activeCheckin == null ? session.getCheckinId() : activeCheckin.getId());
        snapshot.setCheckedCount(getCheckedCount(session, activeCheckin));
        snapshot.setTotalCount(getActiveStudentCount(session.getCourseId()));
        snapshot.setOnlineCount(countOnline(session.getId()));
        snapshot.setHandCount(countPendingInteractions(session.getId(), LiveInteraction.TYPE_HAND));
        snapshot.setPendingQuestionCount(countPendingInteractions(session.getId(), LiveInteraction.TYPE_QUESTION));
        snapshot.setCurrentQuiz(loadCurrentQuiz(session));
        if (currentPage > 0) {
            LiveSlide slide = liveSlideMapper.selectOne(
                    new LambdaQueryWrapper<LiveSlide>()
                            .eq(LiveSlide::getSessionId, session.getId())
                            .eq(LiveSlide::getPageNo, currentPage)
            );
            if (slide != null) {
                snapshot.setCurrentSlide(toSlideVO(slide));
            }
        }
        snapshot.setUpdatedAt(System.currentTimeMillis());
        return snapshot;
    }

    private LiveStateVO assembleState(LiveSession session, Course course, LiveStateSnapshot snapshot) {
        LiveStateVO vo = new LiveStateVO();
        vo.setSessionId(session.getId());
        vo.setCourseId(session.getCourseId());
        vo.setCourseName(course.getName());
        vo.setTitle(session.getTitle());
        vo.setJoinCode(session.getJoinCode());
        vo.setStatus(session.getStatus());
        vo.setCurrentPage(snapshot.getCurrentPage());
        vo.setSlideTotal(snapshot.getSlideTotal());
        vo.setCheckinId(snapshot.getCheckinId());
        vo.setCheckedCount(snapshot.getCheckedCount());
        vo.setTotalCount(snapshot.getTotalCount());
        vo.setOnlineCount(snapshot.getOnlineCount());
        vo.setHandCount(snapshot.getHandCount());
        vo.setPendingQuestionCount(snapshot.getPendingQuestionCount());
        if (snapshot.getCurrentSlide() != null) {
            vo.setCurrentSlide(snapshot.getCurrentSlide());
        }
        if (snapshot.getCurrentQuiz() != null) {
            vo.setCurrentQuiz(copyQuiz(snapshot.getCurrentQuiz()));
        }
        return vo;
    }

    private LiveSlideVO toSlideVO(LiveSlide slide) {
        LiveSlideVO vo = new LiveSlideVO();
        vo.setId(slide.getId());
        vo.setPageNo(slide.getPageNo());
        vo.setImageUrl(fileUrlSigner.sign(slide.getImageUrl()));
        vo.setFileName(slide.getFileName());
        vo.setCreateTime(slide.getCreateTime());
        return vo;
    }

    private String getCheckinCode(Long checkinId) {
        if (checkinId == null) {
            return null;
        }
        Checkin checkin = checkinMapper.selectById(checkinId);
        return checkin == null ? null : checkin.getCode();
    }

    private boolean hasChecked(Long checkinId, Long studentId) {
        if (checkinId == null || studentId == null) {
            return false;
        }
        Long count = checkinRecordMapper.selectCount(
                new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getCheckinId, checkinId)
                        .eq(CheckinRecord::getStudentId, studentId)
                        .in(CheckinRecord::getStatus, 1, 2)
        );
        return count != null && count > 0;
    }

    private int getCheckedCount(LiveSession session, Checkin activeCheckin) {
        List<Long> checkinIds = classCheckinIds(session, activeCheckin);
        if (checkinIds.isEmpty()) {
            return 0;
        }
        List<Object> studentIds = checkinRecordMapper.selectObjs(
                new LambdaQueryWrapper<CheckinRecord>()
                        .select(CheckinRecord::getStudentId)
                        .in(CheckinRecord::getCheckinId, checkinIds)
                        .in(CheckinRecord::getStatus, 1, 2)
                        .groupBy(CheckinRecord::getStudentId)
        );
        return studentIds == null ? 0 : studentIds.size();
    }

    private int getActiveStudentCount(Long courseId) {
        Long count = courseStudentMapper.selectCount(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStatus, 1)
        );
        return count == null ? 0 : count.intValue();
    }
}