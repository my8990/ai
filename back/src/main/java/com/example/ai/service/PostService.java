package com.example.ai.service;

import com.example.ai.dto.CommentCreateDTO;
import com.example.ai.dto.CommentVO;
import com.example.ai.dto.PostCreateDTO;
import com.example.ai.dto.PostVO;

import java.util.List;

public interface PostService {

    PostVO createPost(PostCreateDTO dto, Long userId, String role);

    List<PostVO> getCoursePosts(Long courseId, Long userId, String role, String authorRole, String timeRange);

    PostVO getPostDetail(Long postId, Long userId, String role);

    void deletePost(Long postId, Long userId, String role);

    CommentVO addComment(CommentCreateDTO dto, Long userId, String role);

    void deleteComment(Long commentId, Long userId, String role);

    boolean toggleLike(Long postId, Long userId, String role);

    List<PostVO> getMyAllPosts(Long userId, String role);
}