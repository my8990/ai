package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.AppealDTO;
import com.example.ai.dto.AppealHandleDTO;
import com.example.ai.dto.AppealVO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.security.RequireRole;
import com.example.ai.security.Roles;
import com.example.ai.service.AppealService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "申诉接口")
@RestController
@RequestMapping("/appeal")
@RequiredArgsConstructor
public class AppealController {

    private final AppealService appealService;

    @Operation(summary = "提交申诉")
    @PostMapping("/submit")
    public Result<Void> submit(@Valid @RequestBody AppealDTO dto, @CurrentUser LoginUser loginUser) {
        appealService.submitAppeal(loginUser.id(), dto);
        return Result.success();
    }

    @Operation(summary = "我的申诉列表")
    @GetMapping("/my")
    public Result<List<AppealVO>> myAppeals(@CurrentUser LoginUser loginUser) {
        return Result.success(appealService.getMyAppeals(loginUser.id()));
    }

    @Operation(summary = "所有申诉列表(管理员)")
    @RequireRole(Roles.ADMIN)
    @GetMapping("/all")
    public Result<List<AppealVO>> allAppeals() {
        return Result.success(appealService.getAllAppeals());
    }

    @Operation(summary = "处理申诉(管理员)")
    @RequireRole(Roles.ADMIN)
    @AuditAction(module = "申诉", action = "处理申诉")
    @PutMapping("/handle")
    public Result<Void> handle(@Valid @RequestBody AppealHandleDTO dto) {
        appealService.handleAppeal(dto);
        return Result.success();
    }
}