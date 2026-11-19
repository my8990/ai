package com.example.ai.service.impl;

import com.example.ai.dto.AiChatDTO;
import com.example.ai.security.Roles;
import com.example.ai.service.AiService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.ExchangeStrategies;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class AiServiceImpl implements AiService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_HISTORY_TURNS = 20;
    private static final int MAX_TURN_LENGTH = 2000;

    private final WebClient webClient;

    @Value("${ai.api-key}")
    private String apiKey;

    @Value("${ai.model}")
    private String model;

    @Value("${ai.base-url}")
    private String baseUrl;

    @Value("${ai.chat-path:/v1/chat/completions}")
    private String chatPath;

    @Value("${ai.provider:deepseek}")
    private String provider;

    @Value("${ai.max-tokens:2048}")
    private int maxTokens;

    @Value("${ai.temperature:0.7}")
    private double temperature;

    @Value("${ai.timeout-seconds:120}")
    private long timeoutSeconds;

    private static final String BASE_PROMPT = """
            你是一个智慧课堂AI助手，专门帮助大学生解答学习问题。
            你的职责包括：
            1. 解释课程中的概念和知识点
            2. 梳理知识体系和学习重点
            3. 推荐高效的学习方法
            4. 解答作业和考试中的疑问
            5. 提供学习规划和时间管理建议

            请用清晰、友好、专业的语气回答问题。回答要简洁有条理，适当使用要点列表。
            涉及代码时请使用代码块并标注语言。
            若用户的问题与学习无关或你不确定答案，请如实说明，不要编造事实。
            """;

    private static final String NOT_CONFIGURED_TIP =
            "AI 助手尚未配置 API Key，请联系管理员在 application.yml 中设置 ai.api-key。";

    private static final String UNAVAILABLE_TIP =
            "抱歉，AI 助手暂时无法回复，请稍后再试。";

    public AiServiceImpl(@Value("${ai.base-url}") String baseUrl,
                         @Value("${ai.max-in-memory-size:4194304}") int maxInMemorySize) {
        this.webClient = WebClient.builder()
                .baseUrl(baseUrl)
                .exchangeStrategies(ExchangeStrategies.builder()
                        .codecs(codecs -> codecs.defaultCodecs().maxInMemorySize(maxInMemorySize))
                        .build())
                .build();
    }

    @Override
    public String chat(String message, List<AiChatDTO.ChatTurn> history, String role) {
        if (!isConfigured()) {
            return NOT_CONFIGURED_TIP;
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = webClient.post()
                    .uri(chatPath)
                    .header("Authorization", "Bearer " + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(buildRequestBody(message, history, role, false))
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block(Duration.ofSeconds(timeoutSeconds));

            if (response != null) {
                return extractContent(response);
            }
        } catch (WebClientResponseException e) {
            log.error("AI对话失败: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
        } catch (Exception e) {
            log.error("AI对话失败: {}", e.getMessage());
        }
        return UNAVAILABLE_TIP;
    }

    @Override
    public Flux<String> chatStream(String message, List<AiChatDTO.ChatTurn> history, String role) {
        if (!isConfigured()) {
            return Flux.just(chunk("error", NOT_CONFIGURED_TIP), chunk("done", ""));
        }
        return webClient.post()
                .uri(chatPath)
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.TEXT_EVENT_STREAM)
                .bodyValue(buildRequestBody(message, history, role, true))
                .retrieve()
                .bodyToFlux(String.class)
                .filter(line -> !line.isBlank())
                .map(this::extractStreamContent)
                .filter(content -> !content.isEmpty())
                .map(content -> chunk("content", content))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .concatWith(Flux.just(chunk("done", "")))
                .onErrorResume(e -> {
                    log.error("AI流式对话失败: {}", e.getMessage());
                    return Flux.just(chunk("error", UNAVAILABLE_TIP), chunk("done", ""));
                });
    }

    @Override
    public Map<String, Object> status() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("configured", isConfigured());
        info.put("provider", provider);
        info.put("model", model);
        return info;
    }

    private String chunk(String type, String content) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", type);
        payload.put("content", content);
        try {
            return MAPPER.writeValueAsString(payload);
        } catch (Exception e) {
            return "{\"type\":\"content\",\"content\":\"\"}";
        }
    }

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !"your-api-key-here".equals(apiKey);
    }

    private String buildSystemPrompt(String role) {
        StringBuilder sb = new StringBuilder(BASE_PROMPT);
        if (Roles.TEACHER.equals(role)) {
            sb.append("\n当前对话对象是教师用户，可适当提供教学设计、课堂组织、作业与评价方面的建议。");
        } else if (Roles.STUDENT.equals(role)) {
            sb.append("\n当前对话对象是学生用户，请侧重知识理解与学习方法的引导。");
        }
        return sb.toString();
    }

    private Map<String, Object> buildRequestBody(String message, List<AiChatDTO.ChatTurn> history,
                                                 String role, boolean stream) {
        List<Map<String, Object>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", buildSystemPrompt(role)));

        for (AiChatDTO.ChatTurn turn : sanitizeHistory(history)) {
            messages.add(Map.of("role", turn.getRole(), "content", turn.getContent()));
        }
        messages.add(Map.of("role", "user", "content", message));

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("stream", stream);
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        return body;
    }

    private List<AiChatDTO.ChatTurn> sanitizeHistory(List<AiChatDTO.ChatTurn> history) {
        List<AiChatDTO.ChatTurn> valid = new ArrayList<>();
        if (history == null || history.isEmpty()) {
            return valid;
        }
        for (AiChatDTO.ChatTurn turn : history) {
            if (turn == null) continue;
            String role = turn.getRole();
            if (!"user".equals(role) && !"assistant".equals(role)) continue;
            String content = turn.getContent();
            if (content == null || content.isBlank()) continue;
            if (content.length() > MAX_TURN_LENGTH) {
                content = content.substring(0, MAX_TURN_LENGTH);
            }
            AiChatDTO.ChatTurn item = new AiChatDTO.ChatTurn();
            item.setRole(role);
            item.setContent(content);
            valid.add(item);
        }
        if (valid.size() > MAX_HISTORY_TURNS) {
            return new ArrayList<>(valid.subList(valid.size() - MAX_HISTORY_TURNS, valid.size()));
        }
        return valid;
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> messageObj = (Map<String, Object>) choices.get(0).get("message");
                if (messageObj != null) {
                    Object content = messageObj.get("content");
                    if (content != null) {
                        return content.toString();
                    }
                }
            }
            Object error = response.get("error");
            if (error instanceof Map<?, ?> err && err.get("message") != null) {
                log.warn("AI返回错误: {}", err.get("message"));
            }
        } catch (Exception e) {
            log.warn("解析AI响应失败: {}", e.getMessage());
        }
        return "AI响应解析失败";
    }

    private String extractStreamContent(String line) {
        String payload = line;
        if (payload.startsWith("data:")) {
            payload = payload.substring(5).trim();
        }
        if (payload.isEmpty() || "[DONE]".equals(payload)) {
            return "";
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> data = MAPPER.readValue(payload, Map.class);
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) data.get("choices");
            if (choices != null && !choices.isEmpty()) {
                @SuppressWarnings("unchecked")
                Map<String, Object> delta = (Map<String, Object>) choices.get(0).get("delta");
                if (delta != null && delta.get("content") != null) {
                    return delta.get("content").toString();
                }
            }
        } catch (Exception e) {
            return "";
        }
        return "";
    }
}