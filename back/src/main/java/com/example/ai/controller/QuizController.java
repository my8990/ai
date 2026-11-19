package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.*;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quiz")
public class QuizController {

    @Autowired
    private QuizService quizService;

    @AuditAction(module = "测验", action = "创建测验")
    @PostMapping("/create")
    public Result<QuizVO> create(@RequestBody QuizCreateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.createQuiz(dto, loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "测验", action = "编辑测验")
    @PutMapping("/update")
    public Result<QuizVO> update(@RequestBody QuizCreateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.updateQuiz(dto, loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "测验", action = "锁定测验")
    @PostMapping("/publish/{id}")
    public Result<Void> publish(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        quizService.publishQuiz(id, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @AuditAction(module = "测验", action = "删除测验")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        quizService.deleteQuiz(id, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @GetMapping("/course/{courseId}")
    public Result<List<QuizVO>> getCourseQuizzes(@PathVariable Long courseId,
                                                 @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.getCourseQuizzes(courseId, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/detail/{id}")
    public Result<QuizVO> getDetail(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.getQuizDetail(id, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/take/{id}")
    public Result<QuizTakeVO> take(@PathVariable Long id,
                                   @RequestParam(required = false) Long assignmentId,
                                   @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.takeQuiz(id, assignmentId, loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "测验", action = "提交答卷")
    @PostMapping("/submit")
    public Result<QuizRecordVO> submit(@RequestBody QuizSubmitDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.submitQuiz(dto, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/my-record/{quizId}")
    public Result<QuizRecordVO> getMyRecord(@PathVariable Long quizId,
                                            @RequestParam(required = false) Long assignmentId,
                                            @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.getMyRecord(quizId, assignmentId, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/my-all")
    public Result<List<QuizVO>> getMyAll(@CurrentUser LoginUser loginUser) {
        return Result.success(quizService.getMyAllQuizzes(loginUser.id(), loginUser.role()));
    }
}