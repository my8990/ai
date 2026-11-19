package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.*;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.CourseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "课程接口")
@RestController
@RequestMapping("/course")
@RequiredArgsConstructor
public class CourseController {

    private final CourseService courseService;

    @Operation(summary = "创建课程")
    @AuditAction(module = "课程", action = "创建课程")
    @PostMapping("/create")
    public Result<CourseVO> create(@Valid @RequestBody CourseCreateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(courseService.createCourse(dto, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "编辑课程")
    @AuditAction(module = "课程", action = "编辑课程")
    @PutMapping("/update")
    public Result<CourseVO> update(@Valid @RequestBody CourseUpdateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(courseService.updateCourse(dto, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "课程详情")
    @GetMapping("/detail/{courseId}")
    public Result<CourseVO> detail(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(courseService.getCourseDetail(courseId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "我的课程列表")
    @GetMapping("/my")
    public Result<List<CourseVO>> myCourses(@CurrentUser LoginUser loginUser) {
        return Result.success(courseService.getMyCourses(loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "切换课程状态(开放/关闭)")
    @AuditAction(module = "课程", action = "切换课程状态")
    @PutMapping("/toggle/{courseId}")
    public Result<Void> toggle(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        courseService.toggleCourseStatus(courseId, loginUser.id(), loginUser.role());
        return Result.success();
    }

    @Operation(summary = "解散课程")
    @AuditAction(module = "课程", action = "解散课程")
    @DeleteMapping("/dissolve/{courseId}")
    public Result<Void> dissolve(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        courseService.dissolveCourse(courseId, loginUser.id(), loginUser.role());
        return Result.success();
    }

    @Operation(summary = "课程学生名单")
    @GetMapping("/students/{courseId}")
    public Result<List<CourseStudentVO>> students(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(courseService.getStudentList(courseId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "课程学生人数")
    @GetMapping("/count/{courseId}")
    public Result<Integer> count(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(courseService.getStudentCount(courseId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "学生加入课程")
    @PostMapping("/join")
    public Result<Void> join(@Valid @RequestBody JoinCourseDTO dto, @CurrentUser LoginUser loginUser) {
        courseService.joinCourse(dto.getCode(), loginUser.id(), loginUser.role());
        return Result.success();
    }

    @Operation(summary = "学生退课")
    @PutMapping("/quit/{courseId}")
    public Result<Void> quit(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        courseService.quitCourse(courseId, loginUser.id());
        return Result.success();
    }

    @Operation(summary = "教师移除学生")
    @PutMapping("/remove-student/{courseId}/{studentId}")
    public Result<Void> removeStudent(@PathVariable Long courseId, @PathVariable Long studentId,
                                      @CurrentUser LoginUser loginUser) {
        courseService.removeStudent(courseId, studentId, loginUser.id(), loginUser.role());
        return Result.success();
    }
}