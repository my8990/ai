package com.example.ai.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Locale;
import java.util.Set;

@Component
public class FileSecurityFilter extends OncePerRequestFilter {

    private static final String FILE_PATH_PREFIX = "/files/";

    private static final Set<String> BLOCKED_EXT = Set.of(
            ".html", ".htm", ".js", ".mjs", ".svg", ".swf", ".jsp", ".php", ".sh", ".exe", ".bat"
    );

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path == null || !path.startsWith(FILE_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String path = request.getServletPath().toLowerCase(Locale.ROOT);
        String ext = path.contains(".") ? path.substring(path.lastIndexOf(".")) : "";
        if (BLOCKED_EXT.contains(ext)) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            return;
        }
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'; sandbox");
        response.setHeader("Cache-Control", "private, max-age=300");
        filterChain.doFilter(request, response);
    }
}