package com.example.ai.controller;

import com.example.ai.audit.AuditAction;
import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.common.Result;
import com.example.ai.dto.CoursewareUploadVO;
import com.example.ai.security.FileUrlSigner;
import com.example.ai.service.CoursewareConvertService;
import com.example.ai.util.FileSignatureValidator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/file")
@RequiredArgsConstructor
public class FileController {

    private final CoursewareConvertService coursewareConvertService;

    private final FileUrlSigner fileUrlSigner;

    private static final Set<String> ALLOWED_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp",
            ".pdf", ".doc", ".docx", ".xls", ".xlsx", ".ppt", ".pptx",
            ".txt", ".zip", ".rar", ".7z"
    );

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.max-size-mb:20}")
    private long maxSizeMb;

    @AuditAction(module = "文件", action = "上传文件")
    @PostMapping("/upload")
    public Result<String> upload(@RequestParam("file") MultipartFile file) {
        if (file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        long maxBytes = maxSizeMb * 1024L * 1024L;
        if (file.getSize() > maxBytes) {
            throw new BizException(ErrorCode.PAYLOAD_TOO_LARGE, "文件大小不能超过" + maxSizeMb + "MB");
        }
        String originalName = file.getOriginalFilename();
        String ext = "";
        if (originalName != null && originalName.contains(".")) {
            ext = originalName.substring(originalName.lastIndexOf(".")).toLowerCase(Locale.ROOT);
        }
        if (!ALLOWED_EXT.contains(ext)) {
            throw new BizException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "不支持的文件类型");
        }
        verifySignature(file, ext);
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;

        File dir = new File(uploadDir);
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "上传目录创建失败");
        }

        File target = new File(dir, fileName).getAbsoluteFile();
        try (InputStream in = file.getInputStream()) {
            Files.copy(in, target.toPath(), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件上传失败", e);
        }

        String url = fileUrlSigner.signName(fileName);
        return Result.success(url);
    }

    @AuditAction(module = "文件", action = "上传课件")
    @PostMapping("/courseware/upload")
    public Result<CoursewareUploadVO> uploadCourseware(@RequestParam("file") MultipartFile file) {
        CoursewareUploadVO vo = coursewareConvertService.convert(file);
        vo.setDocumentUrl(fileUrlSigner.sign(vo.getDocumentUrl()));
        if (vo.getPageUrls() != null) {
            vo.setPageUrls(vo.getPageUrls().stream().map(fileUrlSigner::sign).toList());
        }
        return Result.success(vo);
    }

    private void verifySignature(MultipartFile file, String ext) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = FileSignatureValidator.readHeader(in);
            if (!FileSignatureValidator.matches(ext, header)) {
                log.warn("文件内容与扩展名不匹配: name={}, ext={}", file.getOriginalFilename(), ext);
                throw new BizException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "文件内容与扩展名不匹配");
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件校验失败", e);
        }
    }
}