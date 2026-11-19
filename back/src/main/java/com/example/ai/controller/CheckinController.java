package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.CheckinCreateDTO;
import com.example.ai.dto.CheckinRecordStatusDTO;
import com.example.ai.dto.CheckinSubmitByCodeDTO;
import com.example.ai.dto.CheckinSubmitDTO;
import com.example.ai.dto.CheckinVO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.CheckinService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "签到接口")
@RestController
@RequestMapping("/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    @Operation(summary = "发布签到")
    @AuditAction(module = "签到", action = "发布签到")
    @PostMapping("/create")
    public Result<CheckinVO> create(@Valid @RequestBody CheckinCreateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(checkinService.createCheckin(dto, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "结束签到")
    @AuditAction(module = "签到", action = "结束签到")
    @PutMapping("/end/{checkinId}")
    public Result<Void> end(@PathVariable Long checkinId, @CurrentUser LoginUser loginUser) {
        checkinService.endCheckin(checkinId, loginUser.id(), loginUser.role());
        return Result.success();
    }

    @Operation(summary = "删除签到")
    @AuditAction(module = "签到", action = "删除签到")
    @DeleteMapping("/delete/{checkinId}")
    public Result<Void> delete(@PathVariable Long checkinId, @CurrentUser LoginUser loginUser) {
        checkinService.deleteCheckin(checkinId, loginUser.id(), loginUser.role());
        return Result.success();
    }

    @Operation(summary = "课程签到列表(教师)")
    @GetMapping("/course/{courseId}")
    public Result<List<CheckinVO>> courseCheckins(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(checkinService.getCourseCheckins(courseId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "签到详情")
    @GetMapping("/detail/{checkinId}")
    public Result<CheckinVO> detail(@PathVariable Long checkinId, @CurrentUser LoginUser loginUser) {
        return Result.success(checkinService.getCheckinDetail(checkinId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "学生签到")
    @PostMapping("/submit")
    public Result<Void> submit(@Valid @RequestBody CheckinSubmitDTO dto, @CurrentUser LoginUser loginUser) {
        checkinService.submitCheckin(dto, loginUser.id(), loginUser.role());
        return Result.success();
    }

    @Operation(summary = "学生按签到码签到")
    @PostMapping("/submit-by-code")
    public Result<Long> submitByCode(@Valid @RequestBody CheckinSubmitByCodeDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(checkinService.submitCheckinByCode(dto, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "教师修改学生签到结果")
    @AuditAction(module = "签到", action = "修改签到结果")
    @PutMapping("/record/status")
    public Result<CheckinVO> updateRecordStatus(@Valid @RequestBody CheckinRecordStatusDTO dto,
                                                @CurrentUser LoginUser loginUser) {
        return Result.success(checkinService.updateRecordStatus(dto, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "我的待签到列表(学生)")
    @GetMapping("/my")
    public Result<List<CheckinVO>> myCheckins(@CurrentUser LoginUser loginUser) {
        return Result.success(checkinService.getMyCheckins(loginUser.id(), loginUser.role()));
    }
}