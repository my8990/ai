package com.example.ai.service.impl;

import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import com.example.ai.dto.CoursewareUploadVO;
import com.example.ai.service.CoursewareConvertService;
import com.example.ai.util.FileSignatureValidator;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class CoursewareConvertServiceImpl implements CoursewareConvertService {

    private static final Set<String> IMAGE_EXT = Set.of(
            ".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp"
    );

    private static final Set<String> PDF_EXT = Set.of(".pdf");

    private static final Set<String> OFFICE_EXT = Set.of(
            ".ppt", ".pptx", ".doc", ".docx", ".rtf", ".odp", ".odt"
    );

    private static final List<String> SOFFICE_CANDIDATES = List.of(
            "C:/Program Files/LibreOffice/program/soffice.exe",
            "C:/Program Files (x86)/LibreOffice/program/soffice.exe",
            "/usr/bin/soffice",
            "/usr/lib/libreoffice/program/soffice",
            "/opt/libreoffice/program/soffice",
            "/Applications/LibreOffice.app/Contents/MacOS/soffice"
    );

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Value("${file.access-url:http://localhost:8080/api/files}")
    private String accessUrl;

    @Value("${app.live.courseware.soffice-path:}")
    private String sofficePath;

    @Value("${app.live.courseware.max-size-mb:50}")
    private int maxSizeMb;

    @Value("${app.live.courseware.max-pages:200}")
    private int maxPages;

    @Value("${app.live.courseware.render-dpi:150}")
    private int renderDpi;

    @Value("${app.live.courseware.convert-timeout-ms:120000}")
    private long convertTimeoutMs;

    @Override
    public CoursewareUploadVO convert(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BizException(ErrorCode.BAD_REQUEST, "文件不能为空");
        }
        if (file.getSize() > maxSizeMb * 1024L * 1024L) {
            throw new BizException(ErrorCode.PAYLOAD_TOO_LARGE,
                    "课件大小不能超过" + maxSizeMb + "MB");
        }
        String ext = resolveExt(file.getOriginalFilename());
        String baseName = UUID.randomUUID().toString().replace("-", "");

        verifySignature(file, ext);

        CoursewareUploadVO vo = new CoursewareUploadVO();
        vo.setFileName(file.getOriginalFilename());
        vo.setTruncated(false);

        File source = saveOriginal(file, baseName, ext);
        vo.setDocumentUrl(toUrl(source));

        if (IMAGE_EXT.contains(ext)) {
            vo.setPageUrls(List.of(toUrl(source)));
            vo.setPageCount(1);
            return vo;
        }

        if (!PDF_EXT.contains(ext) && !OFFICE_EXT.contains(ext)) {
            throw new BizException(ErrorCode.UNSUPPORTED_MEDIA_TYPE,
                    "仅支持 PPT、Word、PDF 及图片格式课件");
        }

        boolean office = !PDF_EXT.contains(ext);
        File pdf = office ? convertToPdf(source, baseName) : source;
        try {
            List<String> urls = renderPages(pdf, baseName, vo);
            vo.setPageUrls(urls);
            vo.setPageCount(urls.size());
            return vo;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            cleanupByPrefix(baseName);
            throw new BizException(ErrorCode.INTERNAL_ERROR, "课件处理失败", e);
        } finally {
            if (office) {
                deleteQuietly(pdf);
            }
        }
    }

    private String resolveExt(String originalName) {
        if (originalName == null || !originalName.contains(".")) {
            throw new BizException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "无法识别的文件类型");
        }
        return originalName.substring(originalName.lastIndexOf('.')).toLowerCase(Locale.ROOT);
    }

    private void verifySignature(MultipartFile file, String ext) {
        try (InputStream in = file.getInputStream()) {
            byte[] header = FileSignatureValidator.readHeader(in);
            if (!FileSignatureValidator.matches(ext, header)) {
                throw new BizException(ErrorCode.UNSUPPORTED_MEDIA_TYPE, "文件内容与扩展名不匹配");
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件校验失败", e);
        }
    }

    private File saveOriginal(MultipartFile file, String baseName, String ext) {
        File dir = uploadDir();
        File target = new File(dir, baseName + ext);
        try {
            file.transferTo(target.getAbsoluteFile());
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文件保存失败", e);
        }
        return target;
    }

    private File uploadDir() {
        File dir = new File(uploadDir).getAbsoluteFile();
        if (!dir.exists() && !dir.mkdirs()) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "上传目录创建失败");
        }
        return dir;
    }

    private File convertToPdf(File source, String baseName) {
        String soffice = resolveSoffice();
        if (soffice == null) {
            cleanupByPrefix(baseName);
            throw new BizException(ErrorCode.BAD_REQUEST,
                    "服务器未安装 LibreOffice，无法转换 PPT/Word，请将文件另存为 PDF 后上传");
        }
        File outDir = uploadDir();
        File logFile = new File(outDir, baseName + "_convert.log");
        File profileDir = new File(outDir, baseName + "_lo");
        List<String> command = new ArrayList<>();
        command.add(soffice);
        command.add("-env:UserInstallation=" + profileDir.toURI());
        command.add("--headless");
        command.add("--norestore");
        command.add("--nologo");
        command.add("--nodefault");
        command.add("--convert-to");
        command.add("pdf");
        command.add("--outdir");
        command.add(outDir.getAbsolutePath());
        command.add(source.getAbsolutePath());

        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .redirectErrorStream(true)
                    .redirectOutput(logFile)
                    .start();
            if (!process.waitFor(convertTimeoutMs, TimeUnit.MILLISECONDS)) {
                throw new BizException(ErrorCode.INTERNAL_ERROR, "文档转换超时，请稍后重试");
            }
            if (process.exitValue() != 0) {
                throw new BizException(ErrorCode.INTERNAL_ERROR,
                        "文档转换失败，请确认文件未损坏" + readLogSuffix(logFile));
            }
        } catch (IOException e) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文档转换失败", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文档转换被中断", e);
        } finally {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
            deleteQuietly(logFile);
            deleteQuietly(profileDir);
        }

        File pdf = new File(outDir, baseName + ".pdf");
        if (!pdf.exists() || pdf.length() <= 0) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "文档转换结果缺失，请稍后重试");
        }
        return pdf;
    }

    private List<String> renderPages(File pdf, String baseName, CoursewareUploadVO vo) throws IOException {
        List<String> urls = new ArrayList<>();
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pageTotal = document.getNumberOfPages();
            if (pageTotal <= 0) {
                throw new BizException(ErrorCode.BAD_REQUEST, "文档中没有可展示的页面");
            }
            int limit = Math.min(pageTotal, maxPages);
            vo.setTruncated(pageTotal > limit);
            PDFRenderer renderer = new PDFRenderer(document);
            File dir = uploadDir();
            for (int i = 0; i < limit; i++) {
                BufferedImage image = renderer.renderImageWithDPI(i, renderDpi);
                File out = new File(dir, baseName + "_p" + (i + 1) + ".png");
                if (!ImageIO.write(image, "png", out)) {
                    throw new BizException(ErrorCode.INTERNAL_ERROR, "课件渲染失败，请稍后重试");
                }
                urls.add(toUrl(out));
            }
        }
        return urls;
    }

    private String resolveSoffice() {
        if (sofficePath != null && !sofficePath.isBlank()) {
            Path path = new File(sofficePath.trim()).toPath();
            return Files.isExecutable(path) ? sofficePath.trim() : null;
        }
        for (String candidate : SOFFICE_CANDIDATES) {
            if (Files.isExecutable(new File(candidate).toPath())) {
                return candidate;
            }
        }
        return null;
    }

    private String readLogSuffix(File logFile) {
        try {
            if (!logFile.exists() || logFile.length() <= 0) {
                return "";
            }
            String text = Files.readString(logFile.toPath(), StandardCharsets.UTF_8).trim();
            if (text.isEmpty()) {
                return "";
            }
            String lastLine = text.substring(text.lastIndexOf('\n') + 1).trim();
            return lastLine.isEmpty() ? "" : "（" + lastLine + "）";
        } catch (Exception e) {
            return "";
        }
    }

    private void cleanupByPrefix(String baseName) {
        File dir = new File(uploadDir).getAbsoluteFile();
        File[] files = dir.listFiles();
        if (files == null) {
            return;
        }
        for (File item : files) {
            if (item.getName().startsWith(baseName)) {
                deleteQuietly(item);
            }
        }
    }

    private void deleteQuietly(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        try {
            if (file.isDirectory()) {
                File[] children = file.listFiles();
                if (children != null) {
                    for (File child : children) {
                        deleteQuietly(child);
                    }
                }
            }
            if (!file.delete()) {
                log.debug("临时文件删除失败: {}", file.getAbsolutePath());
            }
        } catch (Exception e) {
            log.debug("临时文件删除异常: {}", e.getMessage());
        }
    }

    private String toUrl(File file) {
        return accessUrl + "/" + file.getName();
    }
}