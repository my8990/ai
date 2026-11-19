package com.example.ai.service;

import com.example.ai.dto.AiChatDTO;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;

public interface AiService {

    String chat(String message, List<AiChatDTO.ChatTurn> history, String role);

    Flux<String> chatStream(String message, List<AiChatDTO.ChatTurn> history, String role);

    Map<String, Object> status();
}