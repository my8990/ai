package com.example.ai.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class PostVO {

    private Long id;

    private Long courseId;

    private String courseName;

    private String content;

    private List<String> imageList;

    private Long authorId;

    private String authorName;

    private String authorRole;

    private Integer likeCount;

    private Integer commentCount;

    private Boolean liked;

    private Boolean canDelete;

    private LocalDateTime createTime;

    private List<CommentVO> comments;
}