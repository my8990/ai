package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.NoticeCreateDTO;
import com.example.ai.dto.NoticeVO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.NoticeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "课程公告接口")
@RestController
@RequestMapping("/notice")
@RequiredArgsConstructor
public class NoticeController {

    private final NoticeService noticeService;

    @Operation(summary = "课程公告列表(含课堂课件发布公告)")
    @GetMapping("/course/{courseId}")
    public Result<List<NoticeVO>> listByCourse(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(noticeService.listByCourse(courseId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "我所有课程的最新公告(feed页)")
    @GetMapping("/my")
    public Result<List<NoticeVO>> listMyNotices(@CurrentUser LoginUser loginUser) {
        return Result.success(noticeService.listMyNotices(loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "教师发布公告")
    @AuditAction(module = "公告", action = "发布公告")
    @PostMapping("/create")
    public Result<NoticeVO> create(@Valid @RequestBody NoticeCreateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(noticeService.createTeacherNotice(dto, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "教师删除公告")
    @AuditAction(module = "公告", action = "删除公告")
    @DeleteMapping("/{noticeId}")
    public Result<Void> delete(@PathVariable Long noticeId, @CurrentUser LoginUser loginUser) {
        noticeService.deleteNotice(noticeId, loginUser.id(), loginUser.role());
        return Result.success();
    }
}