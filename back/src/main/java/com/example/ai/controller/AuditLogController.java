package com.example.ai.controller;

import com.example.ai.common.PageResult;
import com.example.ai.common.Result;
import com.example.ai.dto.AuditLogQueryDTO;
import com.example.ai.dto.AuditLogVO;
import com.example.ai.security.RequireRole;
import com.example.ai.security.Roles;
import com.example.ai.service.AuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "审计日志接口")
@RestController
@RequestMapping("/audit")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;

    @Operation(summary = "分页查询审计日志(管理员)")
    @RequireRole(Roles.ADMIN)
    @GetMapping("/page")
    public Result<PageResult<AuditLogVO>> page(@Valid AuditLogQueryDTO query) {
        return Result.success(auditLogService.page(query));
    }
}