package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class CommentVO {

    private Long id;

    private Long postId;

    private Long authorId;

    private String authorName;

    private String authorRole;

    private String content;

    private Boolean canDelete;

    private LocalDateTime createTime;
}