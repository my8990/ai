package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.QuizAssignDTO;
import com.example.ai.dto.QuizVO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.security.RequireRole;
import com.example.ai.security.Roles;
import com.example.ai.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/quiz-assign")
@RequireRole(Roles.TEACHER)
public class QuizAssignmentController {

    @Autowired
    private QuizService quizService;

    @AuditAction(module = "测验", action = "发布到课程")
    @PostMapping("/create")
    public Result<QuizVO> create(@RequestBody QuizAssignDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.assignQuiz(dto, loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "测验", action = "修改发布设置")
    @PutMapping("/update")
    public Result<QuizVO> update(@RequestBody QuizAssignDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.updateAssignment(dto, loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "测验", action = "发布测验")
    @PostMapping("/publish/{id}")
    public Result<Void> publish(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        quizService.publishAssignment(id, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @AuditAction(module = "测验", action = "结束测验")
    @PostMapping("/end/{id}")
    public Result<Void> end(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        quizService.endAssignment(id, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @AuditAction(module = "测验", action = "删除发布记录")
    @DeleteMapping("/delete/{id}")
    public Result<Void> delete(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        quizService.deleteAssignment(id, loginUser.id(), loginUser.role());
        return Result.success(null);
    }
}