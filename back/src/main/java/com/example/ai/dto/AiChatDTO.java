package com.example.ai.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class AiChatDTO {

    @NotBlank(message = "消息不能为空")
    @Size(max = 4000, message = "单条消息请控制在 4000 字以内")
    private String message;

    @Valid
    @Size(max = 40, message = "历史消息条数过多")
    private List<ChatTurn> history;

    @Data
    public static class ChatTurn {

        private String role;

        @Size(max = 4000, message = "历史消息过长")
        private String content;
    }
}