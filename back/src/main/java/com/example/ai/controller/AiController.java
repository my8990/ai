package com.example.ai.controller;

import com.example.ai.common.Result;
import com.example.ai.dto.AiChatDTO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.AiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "AI 助手接口")
@RestController
@RequestMapping("/ai")
@RequiredArgsConstructor
public class AiController {

    private final AiService aiService;

    @Operation(summary = "健康检查")
    @GetMapping("/ping")
    public Result<Map<String, String>> ping() {
        return Result.success(Map.of("message", "pong", "status", "ok"));
    }

    @Operation(summary = "AI 助手配置状态")
    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        return Result.success(aiService.status());
    }

    @Operation(summary = "AI 对话（普通模式）")
    @PostMapping("/chat")
    public Result<Map<String, String>> chat(@Valid @RequestBody AiChatDTO dto, @CurrentUser LoginUser loginUser) {
        String reply = aiService.chat(dto.getMessage(), dto.getHistory(), loginUser.role());
        return Result.success(Map.of("reply", reply));
    }

    @Operation(summary = "AI 对话（流式 SSE）")
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.http.ResponseEntity<reactor.core.publisher.Flux<String>> chatStream(
            @Valid @RequestBody AiChatDTO dto, @CurrentUser LoginUser loginUser) {
        reactor.core.publisher.Flux<String> stream = aiService.chatStream(dto.getMessage(), dto.getHistory(), loginUser.role());
        return org.springframework.http.ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .body(stream);
    }
}