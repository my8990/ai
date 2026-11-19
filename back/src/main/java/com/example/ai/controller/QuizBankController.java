package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.QuizVO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.security.RequireRole;
import com.example.ai.security.Roles;
import com.example.ai.service.QuizService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/quiz-bank")
@RequireRole(Roles.TEACHER)
public class QuizBankController {

    @Autowired
    private QuizService quizService;

    @GetMapping("/my")
    public Result<List<QuizVO>> myQuizzes(@CurrentUser LoginUser loginUser) {
        return Result.success(quizService.getMyQuizzes(loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "测验", action = "公开测验")
    @PostMapping("/share/{id}")
    public Result<Void> share(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        quizService.shareQuiz(id, true, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @AuditAction(module = "测验", action = "取消公开")
    @PostMapping("/unshare/{id}")
    public Result<Void> unshare(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        quizService.shareQuiz(id, false, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @GetMapping("/market")
    public Result<List<QuizVO>> market(@RequestParam(required = false) String keyword,
                                       @RequestParam(required = false) String sort,
                                       @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.getMarketQuizzes(keyword, sort, loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "测验", action = "收藏测验")
    @PostMapping("/favorite/{id}")
    public Result<Void> favorite(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        quizService.favoriteQuiz(id, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @AuditAction(module = "测验", action = "取消收藏")
    @DeleteMapping("/favorite/{id}")
    public Result<Void> unfavorite(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        quizService.unfavoriteQuiz(id, loginUser.id(), loginUser.role());
        return Result.success(null);
    }

    @GetMapping("/favorites")
    public Result<List<QuizVO>> favorites(@CurrentUser LoginUser loginUser) {
        return Result.success(quizService.getMyFavorites(loginUser.id(), loginUser.role()));
    }

    @AuditAction(module = "测验", action = "引用测验")
    @PostMapping("/fork/{id}")
    public Result<QuizVO> fork(@PathVariable Long id, @CurrentUser LoginUser loginUser) {
        return Result.success(quizService.forkQuiz(id, loginUser.id(), loginUser.role()));
    }
}