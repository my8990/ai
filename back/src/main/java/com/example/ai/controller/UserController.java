package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.RequestKeys;
import com.example.ai.common.Result;
import com.example.ai.dto.BanDTO;
import com.example.ai.dto.LoginDTO;
import com.example.ai.dto.RegisterDTO;
import com.example.ai.dto.UserVO;
import com.example.ai.dto.WxLoginDTO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.security.RequireRole;
import com.example.ai.security.Roles;
import com.example.ai.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "用户接口")
@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<UserVO> login(@Valid @RequestBody LoginDTO dto) {
        return Result.success(userService.login(dto));
    }

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<UserVO> register(@Valid @RequestBody RegisterDTO dto) {
        return Result.success(userService.register(dto));
    }

    @Operation(summary = "获取当前用户信息")
    @GetMapping("/info")
    public Result<UserVO> info(@CurrentUser LoginUser loginUser) {
        return Result.success(userService.getCurrentUser(loginUser.id()));
    }

    @Operation(summary = "退出登录")
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        String tokenId = (String) request.getAttribute(RequestKeys.TOKEN_ID);
        Object expire = request.getAttribute(RequestKeys.TOKEN_EXPIRE_SECONDS);
        userService.logout(tokenId, expire instanceof Long value ? value : 0L);
        return Result.success();
    }

    @Operation(summary = "微信登录")
    @PostMapping("/wx-login")
    public Result<UserVO> wxLogin(@Valid @RequestBody WxLoginDTO dto) {
        return Result.success(userService.wxLogin(dto.getCode()));
    }

    @Operation(summary = "待审核教师列表")
    @RequireRole(Roles.ADMIN)
    @GetMapping("/pending-teachers")
    public Result<List<UserVO>> pendingTeachers() {
        return Result.success(userService.getPendingTeachers());
    }

    @Operation(summary = "审核通过教师")
    @RequireRole(Roles.ADMIN)
    @AuditAction(module = "用户", action = "审核通过教师")
    @PutMapping("/approve/{userId}")
    public Result<Void> approve(@PathVariable Long userId) {
        userService.approveTeacher(userId);
        return Result.success();
    }

    @Operation(summary = "驳回教师")
    @RequireRole(Roles.ADMIN)
    @AuditAction(module = "用户", action = "驳回教师")
    @PutMapping("/reject/{userId}")
    public Result<Void> reject(@PathVariable Long userId) {
        userService.rejectTeacher(userId);
        return Result.success();
    }

    @Operation(summary = "所有教师列表")
    @RequireRole(Roles.ADMIN)
    @GetMapping("/teachers")
    public Result<List<UserVO>> allTeachers() {
        return Result.success(userService.getAllTeachers());
    }

    @Operation(summary = "所有用户列表")
    @RequireRole(Roles.ADMIN)
    @GetMapping("/all")
    public Result<List<UserVO>> allUsers() {
        return Result.success(userService.getAllUsers());
    }

    @Operation(summary = "禁用账号")
    @RequireRole(Roles.ADMIN)
    @AuditAction(module = "用户", action = "禁用账号")
    @PutMapping("/ban")
    public Result<Void> ban(@Valid @RequestBody BanDTO dto) {
        userService.banUser(dto.getUserId(), dto.getReason());
        return Result.success();
    }

    @Operation(summary = "启用账号")
    @RequireRole(Roles.ADMIN)
    @AuditAction(module = "用户", action = "启用账号")
    @PutMapping("/unban/{userId}")
    public Result<Void> unban(@PathVariable Long userId) {
        userService.unbanUser(userId);
        return Result.success();
    }
}