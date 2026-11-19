package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.*;
import com.example.ai.entity.ScoreRule;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.ScoreRuleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/score-rule")
public class ScoreRuleController {

    @Autowired
    private ScoreRuleService scoreRuleService;

    @AuditAction(module = "成绩", action = "设置评分规则")
    @PostMapping("/set")
    public Result<ScoreRule> setRule(@RequestBody ScoreRuleDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(scoreRuleService.setRule(dto, loginUser.id(), loginUser.role()));
    }

    @GetMapping("/get/{courseId}")
    public Result<ScoreRule> getRule(@PathVariable Long courseId, @CurrentUser LoginUser loginUser) {
        return Result.success(scoreRuleService.getRule(courseId, loginUser.id(), loginUser.role()));
    }
}