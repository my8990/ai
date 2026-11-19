package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.CheckinQrTokenCodec;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.*;
import com.example.ai.entity.Checkin;
import com.example.ai.entity.CheckinRecord;
import com.example.ai.entity.Course;
import com.example.ai.entity.CourseStudent;
import com.example.ai.entity.User;
import com.example.ai.mapper.CheckinMapper;
import com.example.ai.mapper.CheckinRecordMapper;
import com.example.ai.mapper.CourseMapper;
import com.example.ai.mapper.CourseStudentMapper;
import com.example.ai.mapper.UserMapper;
import com.example.ai.service.CheckinService;
import com.example.ai.service.CourseAccessService;
import com.example.ai.security.Roles;
import com.example.ai.util.GeocodeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinServiceImpl implements CheckinService {

    private final CheckinMapper checkinMapper;
    private final CheckinRecordMapper checkinRecordMapper;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final UserMapper userMapper;
    private final CourseAccessService courseAccessService;
    private final CheckinQrTokenCodec qrTokenCodec;
    private final GeocodeUtil geocodeUtil;
    private final TransactionTemplate transactionTemplate;

    @Override
    @Transactional
    public CheckinVO createCheckin(CheckinCreateDTO dto, Long userId, String role) {
        Course course = courseAccessService.requireTeacher(dto.getCourseId(), userId, role);

        int type = dto.getType() == null ? 1 : dto.getType();
        if (type < 1 || type > 3) {
            throw new BizException(ErrorCode.BAD_REQUEST, "签到类型取值为 1数字签到 2二维码签到 3扫码或数字");
        }

        Checkin active = findActiveCheckin(dto.getCourseId());
        if (active != null) {
            throw new BizException(ErrorCode.BAD_REQUEST, "本课程已有进行中的签到，请先结束后再发布");
        }

        Checkin checkin = new Checkin();
        checkin.setCourseId(dto.getCourseId());
        checkin.setTitle(dto.getTitle());
        checkin.setType(type);
        checkin.setLateMinutes(dto.getLateMinutes() == null || dto.getLateMinutes() < 0 ? 0 : dto.getLateMinutes());
        checkin.setStatus(1);
        checkin.setTeacherId(userId);

        if (dto.getExpireMinutes() != null && dto.getExpireMinutes() > 0) {
            checkin.setExpireTime(LocalDateTime.now().plusMinutes(dto.getExpireMinutes()));
        }

        insertWithUniqueCode(checkin, dto.getCourseId());

        return toVO(checkin, course.getName(), 0, getActiveStudentCount(dto.getCourseId()), true, true);
    }

    private void insertWithUniqueCode(Checkin checkin, Long courseId) {
        for (int attempt = 0; attempt < 3; attempt++) {
            checkin.setId(null);
            checkin.setCode(generateUniqueCheckinCode());
            try {
                checkinMapper.insert(checkin);
                return;
            } catch (DuplicateKeyException e) {
                String message = e.getMessage() == null ? "" : e.getMessage();
                if (message.contains("uk_code_active")) {
                    log.warn("签到码冲突，重新生成，courseId={}，第{}次尝试", courseId, attempt + 1);
                    continue;
                }
                throw new BizException(ErrorCode.BAD_REQUEST, "本课程已有进行中的签到，请先结束后再发布");
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "签到码生成失败，请稍后重试");
    }

    private Checkin findActiveCheckin(Long courseId) {
        List<Checkin> actives = checkinMapper.selectList(
                new LambdaQueryWrapper<Checkin>()
                        .eq(Checkin::getCourseId, courseId)
                        .eq(Checkin::getStatus, 1)
                        .orderByDesc(Checkin::getId)
        );
        Checkin result = null;
        for (Checkin item : actives) {
            if (expireIfStale(item)) {
                continue;
            }
            if (result == null) {
                result = item;
            }
        }
        return result;
    }

    private boolean expireIfStale(Checkin checkin) {
        if (checkin == null || checkin.getStatus() == null || checkin.getStatus() != 1) {
            return false;
        }
        if (checkin.getExpireTime() == null || !LocalDateTime.now().isAfter(checkin.getExpireTime())) {
            return false;
        }
        checkin.setStatus(0);
        checkinMapper.updateById(checkin);
        return true;
    }

    @Override
    public void endCheckin(Long checkinId, Long userId, String role) {
        if (!"teacher".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有教师才能结束签到");
        }

        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "签到活动不存在");
        }
        if (!checkin.getTeacherId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能结束自己发布的签到");
        }
        if (checkin.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "签到已结束");
        }

        checkin.setStatus(0);
        checkinMapper.updateById(checkin);
    }

    @Override
    @Transactional
    public void deleteCheckin(Long checkinId, Long userId, String role) {
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "签到活动不存在");
        }
        courseAccessService.requireTeacher(checkin.getCourseId(), userId, role);

        if (expireIfStale(checkin)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "签到已过期，请刷新后重试");
        }
        if (checkin.getStatus() != null && checkin.getStatus() == 1) {
            throw new BizException(ErrorCode.BAD_REQUEST, "进行中的签到不能删除，请先结束签到");
        }

        checkinRecordMapper.delete(
                new LambdaQueryWrapper<CheckinRecord>().eq(CheckinRecord::getCheckinId, checkinId)
        );
        checkinMapper.deleteById(checkinId);
    }

    @Override
    public List<CheckinVO> getCourseCheckins(Long courseId, Long userId, String role) {
        Course course = courseAccessService.requireTeacher(courseId, userId, role);

        List<Checkin> list = checkinMapper.selectList(
                new LambdaQueryWrapper<Checkin>()
                        .eq(Checkin::getCourseId, courseId)
                        .orderByDesc(Checkin::getCreateTime)
        );

        int totalStudents = getActiveStudentCount(courseId);
        java.util.Map<Long, Integer> checkedCountMap = new java.util.HashMap<>();
        if (!list.isEmpty()) {
            List<Long> checkinIds = list.stream().map(Checkin::getId).toList();
            List<CheckinRecord> records = checkinRecordMapper.selectList(
                    new LambdaQueryWrapper<CheckinRecord>().in(CheckinRecord::getCheckinId, checkinIds)
            );
            for (CheckinRecord r : records) {
                if (isAttended(r.getStatus())) {
                    checkedCountMap.merge(r.getCheckinId(), 1, Integer::sum);
                }
            }
        }
        List<CheckinVO> result = new ArrayList<>();
        java.util.Map<Long, Integer> lateCountMap = countLateBatch(list.stream().map(Checkin::getId).toList());
        for (Checkin c : list) {
            expireIfStale(c);
            CheckinVO vo = toVO(c, course.getName(), checkedCountMap.getOrDefault(c.getId(), 0), totalStudents, true, true);
            vo.setLateCount(lateCountMap.getOrDefault(c.getId(), 0));
            result.add(vo);
        }
        return result;
    }

    @Override
    public CheckinVO getCheckinDetail(Long checkinId, Long userId, String role) {
        Checkin checkin = checkinMapper.selectById(checkinId);
        if (checkin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "签到活动不存在");
        }

        if ("teacher".equals(role)) {
            courseAccessService.requireTeacher(checkin.getCourseId(), userId, role);
        } else {
            courseAccessService.requireMember(checkin.getCourseId(), userId, role);
        }

        Course course = courseMapper.selectById(checkin.getCourseId());
        String courseName = course != null ? course.getName() : "";

        int totalStudents = getActiveStudentCount(checkin.getCourseId());
        int checkedCount = getCheckedCount(checkinId);

        expireIfStale(checkin);
        boolean showCode = "teacher".equals(role);
        CheckinVO vo = toVO(checkin, courseName, checkedCount, totalStudents, showCode, showCode);
        vo.setLateCount(countLate(checkinId));

        List<CheckinRecord> records = checkinRecordMapper.selectList(
                new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getCheckinId, checkinId)
        );
        java.util.Map<Long, CheckinRecord> recordMap = new java.util.HashMap<>();
        for (CheckinRecord r : records) {
            recordMap.put(r.getStudentId(), r);
        }

        List<CourseStudent> courseStudents = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, checkin.getCourseId())
                        .eq(CourseStudent::getStatus, 1)
        );

        List<CheckinRecordVO> recordVOs = new ArrayList<>();
        if ("teacher".equals(role)) {
            List<Long> studentIds = courseStudents.stream().map(CourseStudent::getStudentId).toList();
            java.util.Map<Long, User> studentMap = new java.util.HashMap<>();
            if (!studentIds.isEmpty()) {
                List<User> studentList = userMapper.selectBatchIds(studentIds);
                for (User u : studentList) {
                    studentMap.put(u.getId(), u);
                }
            }
            for (CourseStudent cs : courseStudents) {
                User student = studentMap.get(cs.getStudentId());
                if (student == null) continue;
                CheckinRecordVO rvo = new CheckinRecordVO();
                rvo.setStudentId(student.getId());
                rvo.setUsername(student.getUsername());
                rvo.setNickname(student.getNickname());
                rvo.setStudentNo(student.getStudentNo());
                CheckinRecord record = recordMap.get(student.getId());
                if (record != null) {
                    rvo.setId(record.getId());
                    rvo.setChecked(isAttended(record.getStatus()));
                    rvo.setStatus(record.getStatus() == null ? 1 : record.getStatus());
                    rvo.setCheckinTime(record.getCreateTime());
                    rvo.setLatitude(record.getLatitude());
                    rvo.setLongitude(record.getLongitude());
                    rvo.setAddress(record.getAddress());
                } else {
                    rvo.setChecked(false);
                    rvo.setCheckinTime(null);
                }
                recordVOs.add(rvo);
            }
            recordVOs.sort((a, b) -> {
                if (a.getChecked() && !b.getChecked()) return -1;
                if (!a.getChecked() && b.getChecked()) return 1;
                if (a.getChecked() && b.getChecked()) {
                    return Integer.compare(b.getStatus() == null ? 1 : b.getStatus(),
                            a.getStatus() == null ? 1 : a.getStatus());
                }
                return 0;
            });
        } else {
            User self = userMapper.selectById(userId);
            CheckinRecord myRecord = recordMap.get(userId);
            CheckinRecordVO rvo = new CheckinRecordVO();
            rvo.setStudentId(userId);
            if (self != null) {
                rvo.setUsername(self.getUsername());
                rvo.setNickname(self.getNickname());
                rvo.setStudentNo(self.getStudentNo());
            }
            if (myRecord != null) {
                rvo.setId(myRecord.getId());
                rvo.setChecked(isAttended(myRecord.getStatus()));
                rvo.setStatus(myRecord.getStatus() == null ? 1 : myRecord.getStatus());
                rvo.setCheckinTime(myRecord.getCreateTime());
                rvo.setLatitude(myRecord.getLatitude());
                rvo.setLongitude(myRecord.getLongitude());
                rvo.setAddress(myRecord.getAddress());
            } else {
                rvo.setChecked(false);
                rvo.setCheckinTime(null);
            }
            recordVOs.add(rvo);
        }
        vo.setRecords(recordVOs);

        return vo;
    }

    @Override
    public void submitCheckin(CheckinSubmitDTO dto, Long userId, String role) {
        if (!Roles.STUDENT.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有学生才能签到");
        }

        Checkin checkin = checkinMapper.selectById(dto.getCheckinId());
        if (checkin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "签到活动不存在");
        }
        String address = geocodeUtil.ensureAddress(dto.getAddress(), dto.getLatitude(), dto.getLongitude());
        transactionTemplate.executeWithoutResult(status ->
                doSubmit(checkin, dto.getCode(), dto.getSource(), dto.getToken(),
                        dto.getLatitude(), dto.getLongitude(), address, userId));
    }

    @Override
    public Long submitCheckinByCode(CheckinSubmitByCodeDTO dto, Long userId, String role) {
        if (!Roles.STUDENT.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有学生才能签到");
        }

        List<CourseStudent> memberships = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getStudentId, userId)
                        .eq(CourseStudent::getStatus, 1)
        );
        if (memberships.isEmpty()) {
            throw new BizException(ErrorCode.FORBIDDEN, "您未加入该课程，无法签到");
        }
        List<Long> courseIds = memberships.stream()
                .map(CourseStudent::getCourseId)
                .distinct()
                .toList();

        List<Checkin> actives = checkinMapper.selectList(
                new LambdaQueryWrapper<Checkin>()
                        .in(Checkin::getCourseId, courseIds)
                        .eq(Checkin::getStatus, 1)
                        .orderByDesc(Checkin::getId)
        );
        Checkin matched = null;
        boolean hasActive = false;
        for (Checkin item : actives) {
            if (expireIfStale(item)) {
                continue;
            }
            hasActive = true;
            if (matched == null && item.getCode() != null && item.getCode().equals(dto.getCode())) {
                matched = item;
            }
        }
        if (matched == null) {
            throw new BizException(ErrorCode.BAD_REQUEST,
                    hasActive ? "签到码错误" : "签到已结束");
        }

        Checkin target = matched;
        String address = geocodeUtil.ensureAddress(dto.getAddress(), dto.getLatitude(), dto.getLongitude());
        transactionTemplate.executeWithoutResult(status ->
                doSubmit(target, dto.getCode(), dto.getSource(), dto.getToken(),
                        dto.getLatitude(), dto.getLongitude(), address, userId));
        return target.getId();
    }

    private void doSubmit(Checkin checkin, String code, String source, String token,
                          BigDecimal latitude, BigDecimal longitude, String address, Long userId) {
        if (checkin.getStatus() == null || checkin.getStatus() == 0) {
            throw new BizException(ErrorCode.BAD_REQUEST, "签到已结束");
        }
        if (checkin.getExpireTime() != null && LocalDateTime.now().isAfter(checkin.getExpireTime())) {
            expireIfStale(checkin);
            throw new BizException(ErrorCode.BAD_REQUEST, "签到已过期");
        }

        CourseStudent cs = courseStudentMapper.selectOne(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, checkin.getCourseId())
                        .eq(CourseStudent::getStudentId, userId)
                        .eq(CourseStudent::getStatus, 1)
        );
        if (cs == null) {
            throw new BizException(ErrorCode.FORBIDDEN, "您未加入该课程，无法签到");
        }

        requireSourceMatch(checkin, source, token);

        if (checkin.getCode() == null || !checkin.getCode().equals(code)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "签到码错误");
        }

        CheckinRecord existing = checkinRecordMapper.selectOne(
                new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getCheckinId, checkin.getId())
                        .eq(CheckinRecord::getStudentId, userId)
        );
        if (existing != null) {
            if (existing.getStatus() != null && (existing.getStatus() == 3 || existing.getStatus() == 4)) {
                throw new BizException(ErrorCode.BAD_REQUEST,
                        existing.getStatus() == 3
                                ? "老师已将本次签到登记为请假，无法自助签到，请联系老师"
                                : "老师已将本次签到登记为缺勤，无法自助签到，请联系老师");
            }
            throw new BizException(ErrorCode.CONFLICT, "您已签到，不可重复签到");
        }

        CheckinRecord record = new CheckinRecord();
        record.setCheckinId(checkin.getId());
        record.setStudentId(userId);
        record.setStatus(resolveRecordStatus(checkin));
        record.setLatitude(latitude);
        record.setLongitude(longitude);
        record.setAddress(address);
        try {
            checkinRecordMapper.insert(record);
        } catch (DuplicateKeyException e) {
            throw new BizException(ErrorCode.CONFLICT, "您已签到，不可重复签到");
        }
    }

    private void requireSourceMatch(Checkin checkin, String source, String token) {
        int type = checkin.getType() == null ? 1 : checkin.getType();
        boolean fromQr = "qr".equals(source) || "live".equals(source);
        if (type == 2 && !fromQr) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该签到仅支持扫码签到");
        }
        if (type == 1 && fromQr) {
            throw new BizException(ErrorCode.BAD_REQUEST, "该签到仅支持数字签到");
        }
        if (fromQr && !qrTokenCodec.verify(checkin.getId(), checkin.getCode(), token)) {
            throw new BizException(ErrorCode.BAD_REQUEST, "二维码无效或已过期，请重新扫描老师出示的二维码");
        }
    }

    @Override
    @Transactional
    public CheckinVO updateRecordStatus(CheckinRecordStatusDTO dto, Long userId, String role) {
        if (!"teacher".equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有教师才能修改签到结果");
        }
        Checkin checkin = checkinMapper.selectById(dto.getCheckinId());
        if (checkin == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "签到活动不存在");
        }
        if (!checkin.getTeacherId().equals(userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只能修改自己发布的签到");
        }

        CheckinRecord record = checkinRecordMapper.selectOne(
                new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getCheckinId, dto.getCheckinId())
                        .eq(CheckinRecord::getStudentId, dto.getStudentId())
        );
        if (record == null) {
            record = new CheckinRecord();
            record.setCheckinId(dto.getCheckinId());
            record.setStudentId(dto.getStudentId());
            record.setStatus(dto.getStatus());
            record.setAddress("教师手动登记");
            record.setCreateTime(LocalDateTime.now());
            checkinRecordMapper.insert(record);
        } else {
            record.setStatus(dto.getStatus());
            checkinRecordMapper.updateById(record);
        }

        return getCheckinDetail(dto.getCheckinId(), userId, role);
    }

    private boolean isAttended(Integer status) {
        return status != null && (status == 1 || status == 2);
    }

    private int resolveRecordStatus(Checkin checkin) {
        int lateMinutes = checkin.getLateMinutes() == null ? 0 : checkin.getLateMinutes();
        if (lateMinutes <= 0 || checkin.getCreateTime() == null) {
            return 1;
        }
        LocalDateTime lateLine = checkin.getCreateTime().plusMinutes(lateMinutes);
        return LocalDateTime.now().isAfter(lateLine) ? 2 : 1;
    }

    @Override
    public List<CheckinVO> getMyCheckins(Long userId, String role) {
        if (!Roles.STUDENT.equals(role)) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有学生才能查看待签到列表");
        }

        List<CourseStudent> csList = courseStudentMapper.selectList(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getStudentId, userId)
                        .eq(CourseStudent::getStatus, 1)
        );
        if (csList.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> courseIds = csList.stream().map(CourseStudent::getCourseId).distinct().toList();

        List<Checkin> allCheckins = checkinMapper.selectList(
                new LambdaQueryWrapper<Checkin>()
                        .in(Checkin::getCourseId, courseIds)
                        .orderByDesc(Checkin::getCreateTime)
        );

        List<CheckinRecord> myRecords = checkinRecordMapper.selectList(
                new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getStudentId, userId)
        );
        java.util.Set<Long> checkedIds = myRecords.stream()
                .map(CheckinRecord::getCheckinId)
                .collect(java.util.stream.Collectors.toSet());

        List<Course> courses = courseMapper.selectBatchIds(courseIds);
        java.util.Map<Long, String> courseNameMap = new java.util.HashMap<>();
        for (Course course : courses) {
            courseNameMap.put(course.getId(), course.getName());
        }

        java.util.Map<Long, Integer> checkedCountMap = new java.util.HashMap<>();
        if (!allCheckins.isEmpty()) {
            List<Long> checkinIds = allCheckins.stream().map(Checkin::getId).toList();
            List<CheckinRecord> allRecords = checkinRecordMapper.selectList(
                    new LambdaQueryWrapper<CheckinRecord>().in(CheckinRecord::getCheckinId, checkinIds)
            );
            for (CheckinRecord r : allRecords) {
                if (isAttended(r.getStatus())) {
                    checkedCountMap.merge(r.getCheckinId(), 1, Integer::sum);
                }
            }
        }

        java.util.Map<Long, Integer> totalCountMap = new java.util.HashMap<>();
        for (Long courseId : courseIds) {
            totalCountMap.put(courseId, getActiveStudentCount(courseId));
        }

        java.util.Map<Long, CheckinRecord> myRecordMap = new java.util.HashMap<>();
        for (CheckinRecord r : myRecords) {
            myRecordMap.put(r.getCheckinId(), r);
        }

        java.util.Map<Long, Integer> lateCountMap = countLateBatch(allCheckins.stream().map(Checkin::getId).toList());

        List<CheckinVO> result = new ArrayList<>();
        for (Checkin c : allCheckins) {
            expireIfStale(c);
            CheckinVO vo = toVO(
                    c,
                    courseNameMap.getOrDefault(c.getCourseId(), ""),
                    checkedCountMap.getOrDefault(c.getId(), 0),
                    totalCountMap.getOrDefault(c.getCourseId(), 0),
                    isNumberType(c.getType()),
                    false
            );
            vo.setRecords(null);
            vo.setLateCount(lateCountMap.getOrDefault(c.getId(), 0));
            CheckinRecord mine = myRecordMap.get(c.getId());
            vo.setChecked(mine != null && isAttended(mine.getStatus()));
            vo.setMyStatus(mine != null && mine.getStatus() != null ? mine.getStatus() : null);
            if (mine != null) {
                vo.setMyCheckinTime(mine.getCreateTime());
                vo.setMyLatitude(mine.getLatitude());
                vo.setMyLongitude(mine.getLongitude());
                vo.setMyAddress(mine.getAddress());
            }
            result.add(vo);
        }
        return result;
    }

    private boolean isNumberType(Integer type) {
        return type != null && (type == 1 || type == 3);
    }

    private boolean isQrCapable(Integer type) {
        return type != null && (type == 2 || type == 3);
    }

    private String generateCheckinCode() {
        SecureRandom random = new SecureRandom();
        StringBuilder sb = new StringBuilder(6);
        for (int i = 0; i < 6; i++) {
            sb.append(random.nextInt(10));
        }
        return sb.toString();
    }

    private String generateUniqueCheckinCode() {
        for (int i = 0; i < 50; i++) {
            String code = generateCheckinCode();
            Long count = checkinMapper.selectCount(
                    new LambdaQueryWrapper<Checkin>()
                            .eq(Checkin::getStatus, 1)
                            .eq(Checkin::getCode, code)
            );
            if (count == null || count == 0) {
                return code;
            }
        }
        throw new BizException(ErrorCode.BAD_REQUEST, "签到码生成失败，请稍后重试");
    }

    @Scheduled(fixedDelayString = "${app.checkin.sweep-interval-ms:60000}")
    @Transactional
    public void sweepExpiredCheckins() {
        int updated = checkinMapper.update(null,
                new LambdaUpdateWrapper<Checkin>()
                        .eq(Checkin::getStatus, 1)
                        .isNotNull(Checkin::getExpireTime)
                        .lt(Checkin::getExpireTime, LocalDateTime.now())
                        .set(Checkin::getStatus, 0)
        );
        if (updated > 0) {
            log.info("定时清理过期签到 {} 条", updated);
        }
    }

    private int getActiveStudentCount(Long courseId) {
        Long count = courseStudentMapper.selectCount(
                new LambdaQueryWrapper<CourseStudent>()
                        .eq(CourseStudent::getCourseId, courseId)
                        .eq(CourseStudent::getStatus, 1)
        );
        return count == null ? 0 : count.intValue();
    }

    private int getCheckedCount(Long checkinId) {
        Long count = checkinRecordMapper.selectCount(
                new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getCheckinId, checkinId)
                        .in(CheckinRecord::getStatus, 1, 2)
        );
        return count == null ? 0 : count.intValue();
    }

    private int countLate(Long checkinId) {
        Long count = checkinRecordMapper.selectCount(
                new LambdaQueryWrapper<CheckinRecord>()
                        .eq(CheckinRecord::getCheckinId, checkinId)
                        .eq(CheckinRecord::getStatus, 2)
        );
        return count == null ? 0 : count.intValue();
    }

    private java.util.Map<Long, Integer> countLateBatch(List<Long> checkinIds) {
        java.util.Map<Long, Integer> map = new java.util.HashMap<>();
        if (checkinIds.isEmpty()) {
            return map;
        }
        List<CheckinRecord> records = checkinRecordMapper.selectList(
                new LambdaQueryWrapper<CheckinRecord>()
                        .in(CheckinRecord::getCheckinId, checkinIds)
                        .eq(CheckinRecord::getStatus, 2)
        );
        for (CheckinRecord r : records) {
            map.merge(r.getCheckinId(), 1, Integer::sum);
        }
        return map;
    }

    private CheckinVO toVO(Checkin checkin, String courseName, int checkedCount, int totalCount,
                           boolean showCode, boolean showQrToken) {
        CheckinVO vo = new CheckinVO();
        vo.setId(checkin.getId());
        vo.setCourseId(checkin.getCourseId());
        vo.setCourseName(courseName);
        vo.setTitle(checkin.getTitle());
        vo.setType(checkin.getType());
        vo.setLateMinutes(checkin.getLateMinutes() == null ? 0 : checkin.getLateMinutes());
        vo.setCode(showCode ? checkin.getCode() : null);
        vo.setQrToken(showQrToken && isQrCapable(checkin.getType()) && checkin.getCode() != null
                ? qrTokenCodec.mint(checkin.getId(), checkin.getCode())
                : null);
        vo.setStatus(checkin.getStatus());
        vo.setTeacherId(checkin.getTeacherId());
        vo.setExpireTime(checkin.getExpireTime());
        vo.setCheckedCount(checkedCount);
        vo.setLateCount(0);
        vo.setTotalCount(totalCount);
        vo.setCreateTime(checkin.getCreateTime());
        return vo;
    }
}