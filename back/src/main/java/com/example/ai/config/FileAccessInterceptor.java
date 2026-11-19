package com.example.ai.config;

import com.example.ai.common.ErrorCode;
import com.example.ai.security.AuthResponseWriter;
import com.example.ai.security.FileUrlSigner;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileAccessInterceptor implements HandlerInterceptor {

    private static final String FILE_SEGMENT = "/files/";

    private final FileUrlSigner fileUrlSigner;

    private final AuthResponseWriter authResponseWriter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!fileUrlSigner.isEnabled()) {
            return true;
        }

        String path = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && path.startsWith(contextPath)) {
            path = path.substring(contextPath.length());
        }
        int index = path.indexOf(FILE_SEGMENT);
        if (index < 0) {
            return true;
        }
        String fileName = path.substring(index + FILE_SEGMENT.length());
        if (fileName.isBlank()) {
            authResponseWriter.write(response, ErrorCode.FORBIDDEN, "文件地址无效");
            return false;
        }

        String expires = request.getParameter("expires");
        String signature = request.getParameter("sig");
        if (!fileUrlSigner.verify(fileName, expires, signature)) {
            log.warn("文件访问签名校验失败: file={}, ip={}", fileName, request.getRemoteAddr());
            authResponseWriter.write(response, ErrorCode.FORBIDDEN, "文件链接无效或已过期");
            return false;
        }
        return true;
    }
}