package com.example.ai.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.LiveEndPreviewVO;
import com.example.ai.entity.Course;
import com.example.ai.entity.LiveSession;
import com.example.ai.entity.LiveSlide;
import com.example.ai.entity.Material;
import com.example.ai.entity.Notice;
import com.example.ai.mapper.CourseMapper;
import com.example.ai.mapper.LiveSessionMapper;
import com.example.ai.mapper.LiveSlideMapper;
import com.example.ai.mapper.MaterialMapper;
import com.example.ai.mapper.NoticeMapper;
import com.example.ai.service.CourseAccessService;
import com.example.ai.service.CoursewarePublishService;
import com.example.ai.util.PdfMergeUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class CoursewarePublishServiceImpl implements CoursewarePublishService {

    private static final Set<String> OFFICE_EXT = Set.of(
            ".ppt", ".pptx", ".doc", ".docx", ".rtf", ".odp", ".odt"
    );

    private static final Set<String> IMAGE_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("M月d日");

    private static final int COURSEWARE_STATUS_PUBLISHED = 1;

    private static final int COURSEWARE_STATUS_FAILED = 2;

    private final LiveSessionMapper liveSessionMapper;
    private final LiveSlideMapper liveSlideMapper;
    private final MaterialMapper materialMapper;
    private final NoticeMapper noticeMapper;
    private final CourseMapper courseMapper;
    private final CourseAccessService courseAccessService;

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.access-url:http://localhost:8080/api/files}")
    private String accessUrl;

    @Value("${app.live.courseware.render-dpi:150}")
    private int renderDpi;

    @Override
    public LiveEndPreviewVO preview(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);
        Course course = courseMapper.selectById(session.getCourseId());

        List<LiveSlide> slides = loadSlides(sessionId);
        Material existing = findPublished(sessionId);

        LiveEndPreviewVO vo = new LiveEndPreviewVO();
        vo.setSessionId(session.getId());
        vo.setCourseId(session.getCourseId());
        vo.setCourseName(course == null ? "" : course.getName());
        vo.setTitle(session.getTitle());
        vo.setSessionDate(session.getCreateTime() == null ? "" : session.getCreateTime().toLocalDate().toString());
        vo.setHasCourseware(!slides.isEmpty());
        vo.setSlideTotal(slides.size());
        vo.setPublished(existing != null);
        vo.setMaterialId(existing == null ? null : existing.getId());
        vo.setPublishType(resolvePublishType(session));
        vo.setPublishFileName(resolvePublishFileName(session));
        return vo;
    }

    @Override
    @Transactional
    public Long publish(Long sessionId, Long userId, String role) {
        LiveSession session = requireSession(sessionId);
        courseAccessService.requireTeacher(session.getCourseId(), userId, role);

        Material existing = findPublished(sessionId);
        if (existing != null) {
            return existing.getId();
        }

        List<LiveSlide> slides = loadSlides(sessionId);
        if (slides.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "本节课没有课件，无法发布");
        }

        String ext = resolveExt(session);
        boolean reuseOriginal = shouldReuseOriginal(session);
        Material material = new Material();
        material.setCourseId(session.getCourseId());
        material.setTeacherId(session.getTeacherId());
        material.setLiveSessionId(session.getId());
        material.setSource(2);
        material.setStatus(1);
        material.setPageCount(slides.size());
        material.setDescription(buildDescription(session, slides.size()));

        if (reuseOriginal) {
            material.setFileUrl(session.getOriginalDocUrl());
            material.setFileName(resolveOriginalFileName(session, ext));
            material.setFileType(detectType(ext));
            File local = resolveLocalFile(session.getOriginalDocUrl());
            material.setFileSize(local == null ? null : local.length());
        } else {
            File merged = mergeSlidesToPdf(session, slides);
            material.setFileUrl(joinUrl(merged.getName()));
            material.setFileName(resolvePublishFileName(session));
            material.setFileType("pdf");
            material.setFileSize(merged.length());
        }

        material.setTitle(buildTitle(session));

        try {
            materialMapper.insert(material);
        } catch (DuplicateKeyException e) {
            log.warn("课堂课件已存在，复用历史记录: sessionId={}", sessionId);
            materialMapper.reviveByLiveSessionId(sessionId);
            Material revived = findPublished(sessionId);
            if (revived == null) {
                throw new BizException(ErrorCode.BAD_REQUEST, "本节课件已发布过，无需重复发布");
            }
            material.setId(revived.getId());
            materialMapper.updateById(material);
        }

        session.setCoursewareStatus(COURSEWARE_STATUS_PUBLISHED);
        liveSessionMapper.updateById(session);

        createNotice(session, material, slides.size());
        log.info("课堂课件发布成功: sessionId={}, materialId={}, type={}",
                sessionId, material.getId(), material.getFileType());
        return material.getId();
    }

    @Async("coursewareExecutor")
    @Override
    public void publishAsync(Long sessionId, Long userId, String role) {
        try {
            publish(sessionId, userId, role);
        } catch (BizException e) {
            log.warn("课堂课件发布跳过: sessionId={}, reason={}", sessionId, e.getMessage());
        } catch (Exception e) {
            log.error("课堂课件发布失败: sessionId={}", sessionId, e);
            markFailed(sessionId);
        }
    }

    private void markFailed(Long sessionId) {
        LiveSession session = liveSessionMapper.selectById(sessionId);
        if (session == null) {
            return;
        }
        session.setCoursewareStatus(COURSEWARE_STATUS_FAILED);
        liveSessionMapper.updateById(session);
    }

    private void createNotice(LiveSession session, Material material, int pages) {
        Notice notice = new Notice();
        notice.setCourseId(session.getCourseId());
        notice.setType(Notice.TYPE_COURSEWARE);
        notice.setSessionId(session.getId());
        notice.setMaterialId(material.getId());
        notice.setTeacherId(session.getTeacherId());
        LocalDateTime baseTime = session.getCreateTime() == null ? LocalDateTime.now() : session.getCreateTime();
        notice.setTitle(baseTime.format(DATE_FORMATTER) + "课堂课件已发布");
        Course course = courseMapper.selectById(session.getCourseId());
        String courseName = course == null || course.getName() == null ? "" : course.getName();
        notice.setContent("《" + courseName + "》课堂课件已发布，共" + pages + "页，可前往课程课件查看学习。");
        try {
            noticeMapper.insert(notice);
        } catch (DuplicateKeyException e) {
            log.debug("课堂课件公告已存在，跳过: sessionId={}", session.getId());
        }
    }

    private File mergeSlidesToPdf(LiveSession session, List<LiveSlide> slides) {
        List<File> images = new ArrayList<>();
        for (LiveSlide slide : slides) {
            File image = resolveLocalFile(slide.getImageUrl());
            if (image != null) {
                images.add(image);
            }
        }
        if (images.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "课件图片文件缺失，无法生成 PDF");
        }
        File dir = uploadDir();
        File target = new File(dir, "cw_" + UUID.randomUUID().toString().replace("-", "") + ".pdf");
        try {
            PdfMergeUtil.mergeImages(images, target, renderDpi);
        } catch (Exception e) {
            deleteQuietly(target);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "课件 PDF 生成失败", e);
        }
        return target;
    }

    private boolean shouldReuseOriginal(LiveSession session) {
        if (session.getOriginalDocUrl() == null) {
            return false;
        }
        String ext = resolveExt(session);
        return OFFICE_EXT.contains(ext) || ".pdf".equals(ext);
    }

    private String resolvePublishType(LiveSession session) {
        return shouldReuseOriginal(session) ? "original" : "pdf";
    }

    private String resolvePublishFileName(LiveSession session) {
        if (shouldReuseOriginal(session)) {
            return resolveOriginalFileName(session, resolveExt(session));
        }
        String original = session.getOriginalFileName();
        String base = original != null && original.contains(".")
                ? original.substring(0, original.lastIndexOf('.'))
                : (session.getTitle() == null ? "课堂课件" : session.getTitle());
        return clip(base + ".pdf", 200);
    }

    private String resolveOriginalFileName(LiveSession session, String ext) {
        String name = session.getOriginalFileName();
        if (name != null && !name.isBlank()) {
            return clip(name.trim(), 200);
        }
        String title = session.getTitle() == null ? "课堂课件" : session.getTitle();
        return clip(title + ext, 200);
    }

    private String clip(String value, int max) {
        return value.length() > max ? value.substring(0, max) : value;
    }

    private String buildTitle(LiveSession session) {
        String title = session.getTitle() == null || session.getTitle().isBlank()
                ? "课堂课件" : session.getTitle().trim();
        String full = title + " 课件";
        return full.length() > 100 ? full.substring(0, 100) : full;
    }

    private String buildDescription(LiveSession session, int pages) {
        String date = session.getCreateTime() == null ? "" : session.getCreateTime().toLocalDate().toString();
        String text = date + "课堂课件，共" + pages + "页（下课后发布）";
        return text.length() > 500 ? text.substring(0, 500) : text;
    }

    private String resolveExt(LiveSession session) {
        String source = session.getOriginalFileName();
        if (source == null || source.isBlank()) {
            source = session.getOriginalDocUrl();
        }
        if (source == null || !source.contains(".")) {
            return "";
        }
        String tail = source.substring(source.lastIndexOf('.')).toLowerCase(Locale.ROOT);
        int slash = tail.indexOf('/');
        return slash >= 0 ? tail.substring(0, slash) : tail;
    }

    private String detectType(String ext) {
        if (IMAGE_EXT.contains(ext)) {
            return "image";
        }
        if (".pdf".equals(ext)) {
            return "pdf";
        }
        if (".ppt".equals(ext) || ".pptx".equals(ext)) {
            return "ppt";
        }
        if (".doc".equals(ext) || ".docx".equals(ext) || ".rtf".equals(ext)
                || ".odt".equals(ext) || ".odp".equals(ext)) {
            return "word";
        }
        return "other";
    }

    private Material findPublished(Long sessionId) {
        return materialMapper.selectOne(
                new LambdaQueryWrapper<Material>()
                        .eq(Material::getLiveSessionId, sessionId)
                        .last("limit 1")
        );
    }

    private List<LiveSlide> loadSlides(Long sessionId) {
        return liveSlideMapper.selectList(
                new LambdaQueryWrapper<LiveSlide>()
                        .eq(LiveSlide::getSessionId, sessionId)
                        .orderByAsc(LiveSlide::getPageNo)
        );
    }

    private LiveSession requireSession(Long sessionId) {
        LiveSession session = liveSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "课堂不存在");
        }
        return session;
    }

    private File resolveLocalFile(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String path = url.trim();
        int query = path.indexOf('?');
        if (query >= 0) {
            path = path.substring(0, query);
        }
        String name = path.substring(path.lastIndexOf('/') + 1);
        if (name.isEmpty()) {
            return null;
        }
        File file = new File(uploadDir(), name);
        return file.exists() && file.isFile() ? file : null;
    }

    private File uploadDir() {
        File dir = new File(uploadDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "上传目录创建失败");
        }
        return dir;
    }

    private String joinUrl(String fileName) {
        String base = accessUrl.endsWith("/") ? accessUrl.substring(0, accessUrl.length() - 1) : accessUrl;
        return base + "/" + fileName;
    }

    private void deleteQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            if (!file.delete()) {
                log.warn("临时文件删除失败: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.warn("临时文件删除异常: {}", file.getAbsolutePath());
        }
    }
}