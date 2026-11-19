package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.Result;
import com.example.ai.dto.CommentCreateDTO;
import com.example.ai.dto.CommentVO;
import com.example.ai.dto.PostCreateDTO;
import com.example.ai.dto.PostVO;
import com.example.ai.security.CurrentUser;
import com.example.ai.security.LoginUser;
import com.example.ai.service.PostService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "班级讨论接口")
@RestController
@RequestMapping("/post")
@RequiredArgsConstructor
public class PostController {

    private final PostService postService;

    @Operation(summary = "发布帖子")
    @AuditAction(module = "讨论", action = "发布帖子")
    @PostMapping("/create")
    public Result<PostVO> create(@Valid @RequestBody PostCreateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(postService.createPost(dto, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "课程帖子列表")
    @GetMapping("/course/{courseId}")
    public Result<List<PostVO>> list(
            @PathVariable Long courseId,
            @RequestParam(required = false) String authorRole,
            @RequestParam(required = false) String timeRange,
            @CurrentUser LoginUser loginUser) {
        return Result.success(postService.getCoursePosts(courseId, loginUser.id(), loginUser.role(), authorRole, timeRange));
    }

    @Operation(summary = "帖子详情")
    @GetMapping("/detail/{postId}")
    public Result<PostVO> detail(@PathVariable Long postId, @CurrentUser LoginUser loginUser) {
        return Result.success(postService.getPostDetail(postId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "删除帖子(教师)")
    @AuditAction(module = "讨论", action = "删除帖子")
    @DeleteMapping("/delete/{postId}")
    public Result<Void> delete(@PathVariable Long postId, @CurrentUser LoginUser loginUser) {
        postService.deletePost(postId, loginUser.id(), loginUser.role());
        return Result.success();
    }

    @Operation(summary = "评论帖子")
    @PostMapping("/comment")
    public Result<CommentVO> comment(@Valid @RequestBody CommentCreateDTO dto, @CurrentUser LoginUser loginUser) {
        return Result.success(postService.addComment(dto, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "删除评论(教师)")
    @DeleteMapping("/comment/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId, @CurrentUser LoginUser loginUser) {
        postService.deleteComment(commentId, loginUser.id(), loginUser.role());
        return Result.success();
    }

    @Operation(summary = "点赞/取消点赞")
    @PostMapping("/like/{postId}")
    public Result<Boolean> like(@PathVariable Long postId, @CurrentUser LoginUser loginUser) {
        return Result.success(postService.toggleLike(postId, loginUser.id(), loginUser.role()));
    }

    @Operation(summary = "我的所有课程帖子聚合")
    @GetMapping("/my")
    public Result<List<PostVO>> my(@CurrentUser LoginUser loginUser) {
        return Result.success(postService.getMyAllPosts(loginUser.id(), loginUser.role()));
    }
}