package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.CommentCreateDTO;
import com.example.ai.dto.CommentVO;
import com.example.ai.dto.PostCreateDTO;
import com.example.ai.dto.PostVO;
import com.example.ai.entity.Course;
import com.example.ai.entity.CourseStudent;
import com.example.ai.entity.Notice;
import com.example.ai.entity.Post;
import com.example.ai.entity.PostComment;
import com.example.ai.entity.PostLike;
import com.example.ai.entity.User;
import com.example.ai.mapper.CourseMapper;
import com.example.ai.mapper.CourseStudentMapper;
import com.example.ai.mapper.NoticeMapper;
import com.example.ai.mapper.PostCommentMapper;
import com.example.ai.mapper.PostLikeMapper;
import com.example.ai.mapper.PostMapper;
import com.example.ai.mapper.UserMapper;
import com.example.ai.security.FileUrlSigner;
import com.example.ai.security.Roles;
import com.example.ai.service.CourseAccessService;
import com.example.ai.service.PostService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    private final PostMapper postMapper;
    private final PostCommentMapper postCommentMapper;
    private final PostLikeMapper postLikeMapper;
    private final UserMapper userMapper;
    private final CourseMapper courseMapper;
    private final CourseStudentMapper courseStudentMapper;
    private final CourseAccessService courseAccessService;
    private final NoticeMapper noticeMapper;
    private final FileUrlSigner fileUrlSigner;

    @Override
    public PostVO createPost(PostCreateDTO dto, Long userId, String role) {
        Course course = courseAccessService.requireMember(dto.getCourseId(), userId, role);

        Post post = new Post();
        post.setCourseId(dto.getCourseId());
        post.setAuthorId(userId);
        post.setContent(dto.getContent().trim());
        post.setImages(joinImages(dto.getImages()));
        post.setLikeCount(0);
        post.setCommentCount(0);
        postMapper.insert(post);

        if (Roles.TEACHER.equals(role)) {
            try {
                Notice notice = new Notice();
                notice.setCourseId(dto.getCourseId());
                notice.setType(Notice.TYPE_DISCUSSION);
                notice.setTeacherId(userId);
                String title = "老师在《" + course.getName() + "》发布了新讨论";
                notice.setTitle(title);
                String content = dto.getContent().trim();
                if (content.length() > 100) {
                    content = content.substring(0, 100) + "...";
                }
                notice.setContent(content.isEmpty() ? null : content);
                noticeMapper.insert(notice);
            } catch (Exception ignored) {
            }
        }

        return toVO(post, userId, role, course, resolveUsers(List.of(userId)), Set.of(), List.of());
    }

    @Override
    public List<PostVO> getCoursePosts(Long courseId, Long userId, String role, String authorRole, String timeRange) {
        Course course = courseAccessService.requireMember(courseId, userId, role);

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<Post>()
                .eq(Post::getCourseId, courseId);

        if (Roles.TEACHER.equalsIgnoreCase(authorRole)) {
            wrapper.inSql(Post::getAuthorId,
                    "SELECT id FROM user WHERE role = 'teacher' AND deleted = 0");
        } else if (Roles.STUDENT.equalsIgnoreCase(authorRole)) {
            wrapper.inSql(Post::getAuthorId,
                    "SELECT id FROM user WHERE role = 'student' AND deleted = 0");
        }

        LocalDateTime now = LocalDateTime.now();
        if ("today".equalsIgnoreCase(timeRange)) {
            wrapper.ge(Post::getCreateTime, now.toLocalDate().atStartOfDay());
        } else if ("week".equalsIgnoreCase(timeRange)) {
            wrapper.ge(Post::getCreateTime, now.minusWeeks(1));
        } else if ("month".equalsIgnoreCase(timeRange)) {
            wrapper.ge(Post::getCreateTime, now.minusMonths(1));
        }

        wrapper.orderByDesc(Post::getCreateTime);

        List<Post> posts = postMapper.selectList(wrapper);
        if (posts.isEmpty()) {
            return new ArrayList<>();
        }

        List<Long> postIds = posts.stream().map(Post::getId).toList();
        List<PostComment> allComments = postCommentMapper.selectList(
                new LambdaQueryWrapper<PostComment>()
                        .in(PostComment::getPostId, postIds)
                        .orderByAsc(PostComment::getCreateTime)
        );
        Map<Long, List<PostComment>> commentMap = allComments.stream()
                .collect(Collectors.groupingBy(PostComment::getPostId));

        Set<Long> likedPostIds = postLikeMapper.selectList(
                        new LambdaQueryWrapper<PostLike>()
                                .in(PostLike::getPostId, postIds)
                                .eq(PostLike::getUserId, userId)
                ).stream()
                .map(PostLike::getPostId)
                .collect(Collectors.toSet());

        Set<Long> userIds = new HashSet<>();
        posts.forEach(p -> userIds.add(p.getAuthorId()));
        allComments.forEach(c -> userIds.add(c.getAuthorId()));
        Map<Long, User> userMap = resolveUsers(new ArrayList<>(userIds));

        List<PostVO> result = new ArrayList<>();
        for (Post post : posts) {
            List<PostComment> comments = commentMap.getOrDefault(post.getId(), List.of());
            result.add(toVO(post, userId, role, course, userMap, likedPostIds, comments));
        }
        return result;
    }

    @Override
    public PostVO getPostDetail(Long postId, Long userId, String role) {
        Post post = getPostOrThrow(postId);
        Course course = courseAccessService.requireMember(post.getCourseId(), userId, role);

        List<PostComment> comments = postCommentMapper.selectList(
                new LambdaQueryWrapper<PostComment>()
                        .eq(PostComment::getPostId, postId)
                        .orderByAsc(PostComment::getCreateTime)
        );
        Set<Long> userIds = new HashSet<>();
        userIds.add(post.getAuthorId());
        comments.forEach(c -> userIds.add(c.getAuthorId()));
        Map<Long, User> userMap = resolveUsers(new ArrayList<>(userIds));

        boolean liked = postLikeMapper.selectCount(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, userId)
        ) > 0;

        return toVO(post, userId, role, course, userMap, liked ? Set.of(postId) : Set.of(), comments);
    }

    @Override
    @Transactional
    public void deletePost(Long postId, Long userId, String role) {
        Post post = getPostOrThrow(postId);
        Course course = courseAccessService.requireCourse(post.getCourseId());

        boolean isOwnerTeacher = Roles.TEACHER.equals(role) && course.getTeacherId().equals(userId);
        if (!isOwnerTeacher) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有任课教师才能删除帖子");
        }

        postCommentMapper.delete(
                new LambdaQueryWrapper<PostComment>().eq(PostComment::getPostId, postId)
        );
        postLikeMapper.delete(
                new LambdaQueryWrapper<PostLike>().eq(PostLike::getPostId, postId)
        );
        postMapper.deleteById(postId);
    }

    @Override
    public CommentVO addComment(CommentCreateDTO dto, Long userId, String role) {
        Post post = getPostOrThrow(dto.getPostId());
        Course course = courseAccessService.requireMember(post.getCourseId(), userId, role);

        PostComment comment = new PostComment();
        comment.setPostId(dto.getPostId());
        comment.setAuthorId(userId);
        comment.setContent(dto.getContent().trim());
        postCommentMapper.insert(comment);

        post.setCommentCount((post.getCommentCount() == null ? 0 : post.getCommentCount()) + 1);
        postMapper.updateById(post);

        return toCommentVO(comment, userId, role, course, resolveUsers(List.of(userId)));
    }

    @Override
    @Transactional
    public void deleteComment(Long commentId, Long userId, String role) {
        PostComment comment = postCommentMapper.selectById(commentId);
        if (comment == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "评论不存在");
        }
        Post post = getPostOrThrow(comment.getPostId());
        Course course = courseAccessService.requireCourse(post.getCourseId());

        boolean isOwnerTeacher = Roles.TEACHER.equals(role) && course.getTeacherId().equals(userId);
        if (!isOwnerTeacher) {
            throw new BizException(ErrorCode.FORBIDDEN, "只有任课教师才能删除评论");
        }

        postCommentMapper.deleteById(commentId);
        int current = post.getCommentCount() == null ? 0 : post.getCommentCount();
        post.setCommentCount(Math.max(current - 1, 0));
        postMapper.updateById(post);
    }

    @Override
    public boolean toggleLike(Long postId, Long userId, String role) {
        Post post = getPostOrThrow(postId);
        courseAccessService.requireMember(post.getCourseId(), userId, role);

        PostLike existing = postLikeMapper.selectOne(
                new LambdaQueryWrapper<PostLike>()
                        .eq(PostLike::getPostId, postId)
                        .eq(PostLike::getUserId, userId)
        );
        int current = post.getLikeCount() == null ? 0 : post.getLikeCount();
        if (existing != null) {
            postLikeMapper.deleteById(existing.getId());
            post.setLikeCount(Math.max(current - 1, 0));
            postMapper.updateById(post);
            return false;
        }
        PostLike like = new PostLike();
        like.setPostId(postId);
        like.setUserId(userId);
        postLikeMapper.insert(like);
        post.setLikeCount(current + 1);
        postMapper.updateById(post);
        return true;
    }

    private static final int MAX_MY_POSTS = 50;

    @Override
    public List<PostVO> getMyAllPosts(Long userId, String role) {
        List<Long> courseIds;
        if (Roles.TEACHER.equals(role)) {
            List<Course> teacherCourses = courseMapper.selectList(
                    new LambdaQueryWrapper<Course>()
                            .eq(Course::getTeacherId, userId)
                            .eq(Course::getStatus, 1)
            );
            courseIds = teacherCourses.stream().map(Course::getId).collect(Collectors.toList());
        } else if (Roles.STUDENT.equals(role)) {
            List<CourseStudent> relations = courseStudentMapper.selectList(
                    new LambdaQueryWrapper<CourseStudent>()
                            .eq(CourseStudent::getStudentId, userId)
                            .eq(CourseStudent::getStatus, 1)
            );
            if (relations.isEmpty()) return Collections.emptyList();
            List<Long> rawIds = relations.stream().map(CourseStudent::getCourseId).collect(Collectors.toList());
            List<Course> validCourses = courseMapper.selectBatchIds(rawIds);
            courseIds = validCourses.stream()
                    .filter(c -> c.getStatus() == 1)
                    .map(Course::getId)
                    .collect(Collectors.toList());
        } else {
            return Collections.emptyList();
        }

        if (courseIds.isEmpty()) return Collections.emptyList();

        List<Post> posts = postMapper.selectList(
                new LambdaQueryWrapper<Post>()
                        .in(Post::getCourseId, courseIds)
                        .orderByDesc(Post::getCreateTime)
                        .last("limit " + MAX_MY_POSTS)
        );

        if (posts.isEmpty()) return Collections.emptyList();

        List<Long> postIds = posts.stream().map(Post::getId).toList();

        List<PostComment> allComments = postCommentMapper.selectList(
                new LambdaQueryWrapper<PostComment>()
                        .in(PostComment::getPostId, postIds)
                        .orderByAsc(PostComment::getCreateTime)
        );
        Map<Long, List<PostComment>> commentMap = allComments.stream()
                .collect(Collectors.groupingBy(PostComment::getPostId));

        Set<Long> likedPostIds = postLikeMapper.selectList(
                        new LambdaQueryWrapper<PostLike>()
                                .in(PostLike::getPostId, postIds)
                                .eq(PostLike::getUserId, userId)
                ).stream()
                .map(PostLike::getPostId)
                .collect(Collectors.toSet());

        Set<Long> userIds = new HashSet<>();
        posts.forEach(p -> userIds.add(p.getAuthorId()));
        allComments.forEach(c -> userIds.add(c.getAuthorId()));
        Map<Long, User> userMap = resolveUsers(new ArrayList<>(userIds));

        Map<Long, Course> courseMap = courseMapper.selectBatchIds(courseIds).stream()
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        List<PostVO> result = new ArrayList<>();
        for (Post post : posts) {
            Course course = courseMap.get(post.getCourseId());
            List<PostComment> comments = commentMap.getOrDefault(post.getId(), List.of());
            PostVO vo = toVO(post, userId, role, course, userMap, likedPostIds, comments);
            if (course != null) {
                vo.setCourseName(course.getName());
            }
            result.add(vo);
        }
        return result;
    }

    private Post getPostOrThrow(Long postId) {
        Post post = postMapper.selectById(postId);
        if (post == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "帖子不存在或已删除");
        }
        return post;
    }

    private Map<Long, User> resolveUsers(List<Long> userIds) {
        if (userIds.isEmpty()) {
            return new HashMap<>();
        }
        return userMapper.selectBatchIds(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u, (a, b) -> a));
    }

    private PostVO toVO(Post post, Long userId, String role, Course course,
                        Map<Long, User> userMap, Set<Long> likedPostIds, List<PostComment> comments) {
        PostVO vo = new PostVO();
        vo.setId(post.getId());
        vo.setCourseId(post.getCourseId());
        vo.setContent(post.getContent());
        vo.setImageList(parseImages(post.getImages()));
        vo.setAuthorId(post.getAuthorId());
        vo.setLikeCount(post.getLikeCount() == null ? 0 : post.getLikeCount());
        vo.setCommentCount(post.getCommentCount() == null ? 0 : post.getCommentCount());
        vo.setLiked(likedPostIds.contains(post.getId()));
        vo.setCreateTime(post.getCreateTime());

        User author = userMap.get(post.getAuthorId());
        vo.setAuthorName(displayName(author));
        vo.setAuthorRole(resolveAuthorRole(author, course));
        vo.setCanDelete(canDelete(author, course, role, userId));

        List<CommentVO> commentVOs = new ArrayList<>();
        for (PostComment c : comments) {
            commentVOs.add(toCommentVO(c, userId, role, course, userMap));
        }
        vo.setComments(commentVOs);
        return vo;
    }

    private CommentVO toCommentVO(PostComment comment, Long userId, String role, Course course,
                                  Map<Long, User> userMap) {
        CommentVO vo = new CommentVO();
        vo.setId(comment.getId());
        vo.setPostId(comment.getPostId());
        vo.setAuthorId(comment.getAuthorId());
        vo.setContent(comment.getContent());
        vo.setCreateTime(comment.getCreateTime());

        User author = userMap.get(comment.getAuthorId());
        vo.setAuthorName(displayName(author));
        vo.setAuthorRole(resolveAuthorRole(author, course));
        vo.setCanDelete(canDelete(author, course, role, userId));
        return vo;
    }

    private boolean canDelete(User author, Course course, String role, Long userId) {
        if (!Roles.TEACHER.equals(role)) {
            return false;
        }
        if (!course.getTeacherId().equals(userId)) {
            return false;
        }
        return true;
    }

    private String resolveAuthorRole(User author, Course course) {
        if (author == null) {
            return Roles.STUDENT;
        }
        if (Roles.TEACHER.equals(author.getRole()) && course.getTeacherId().equals(author.getId())) {
            return Roles.TEACHER;
        }
        return author.getRole() != null ? author.getRole() : Roles.STUDENT;
    }

    private String displayName(User user) {
        if (user == null) {
            return "已注销用户";
        }
        return user.getNickname() != null && !user.getNickname().isEmpty()
                ? user.getNickname()
                : user.getUsername();
    }

    private String joinImages(List<String> images) {
        if (images == null || images.isEmpty()) {
            return null;
        }
        return images.stream()
                .filter(url -> url != null && !url.isBlank())
                .map(fileUrlSigner::normalize)
                .limit(9)
                .collect(Collectors.joining(","));
    }

    private List<String> parseImages(String images) {
        if (images == null || images.isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(images.split(","))
                .filter(s -> !s.isBlank())
                .map(fileUrlSigner::sign)
                .collect(Collectors.toList());
    }
}