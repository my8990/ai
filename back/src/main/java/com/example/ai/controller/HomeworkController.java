package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.*;
import com.example.ai.entity.Homework;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.HomeworkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/homework")
public class HomeworkController {

    @Autowired
    private HomeworkService homeworkService;

    @AuditAction(module = "作业", action = "发布作业")
    @PostMapping("/create")
    public Result<Homework> create(@RequestBody HomeworkCreateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(homeworkService.createHomework(dto, loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "作业", action = "编辑作业")
    @PutMapping("/update")
    public Result<Homework> update(@RequestBody HomeworkUpdateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(homeworkService.updateHomework(dto, loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "作业", action = "删除作业")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        homeworkService.deleteHomework(id, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @GetMapping("/course/{courseId}")
    public Result<List<HomeworkVO>> getCourseHomework(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(homeworkService.getCourseHomework(courseId, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/detail/{id}")
    public Result<HomeworkVO> getDetail(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        return Result.success(homeworkService.getHomeworkDetail(id, loginUser.id(), loginUser.role()));
    }

    @PostMapping("/submit")
    public Result<Void> submit(@RequestBody HomeworkSubmitDTO dto, @CurrentUser LoginUser loginUser) {
        homeworkService.submitHomework(dto, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @AuditAction(module = "作业", action = "批改作业")
    @PutMapping("/grade")
    public Result<Void> grade(@RequestBody HomeworkGradeDTO dto, @CurrentUser LoginUser loginUser) {
        homeworkService.gradeHomework(dto, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @GetMapping("/submissions/{homeworkId}")
    public Result<List<HomeworkSubmitVO>> getSubmissions(@PathVariable Long homeworkId,
                                                        @CurrentUser LoginUser loginUser) {
        return Result.success(homeworkService.getHomeworkSubmissions(homeworkId, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/my-submit/{homeworkId}")
    public Result<HomeworkSubmitVO> getMySubmit(@PathVariable Long homeworkId, @CurrentUser LoginUser loginUser) {
        return Result.success(homeworkService.getMySubmit(homeworkId, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/my/{courseId}")
    public Result<List<HomeworkVO>> getMyHomework(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(homeworkService.getMyHomework(courseId, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/my-all")
    public Result<List<HomeworkVO>> getMyAllHomework(@CurrentUser LoginUser loginUser) {
        return Result.success(homeworkService.getMyAllHomework(loginUser.id(), loginUser.role()));
    }
}