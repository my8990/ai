package com.example.ai.security;

import com.example.ai.common.ErrorCode;
import com.example.ai.common.RequestKeys;
import com.example.ai.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtInterceptor implements AsyncHandlerInterceptor {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final AuthResponseWriter authResponseWriter;
    private final TokenBlacklistService tokenBlacklistService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = resolveToken(request);
        if (token == null) {
            authResponseWriter.write(response, ErrorCode.UNAUTHORIZED, "未登录，请先登录");
            return false;
        }

        try {
            Claims claims = jwtUtil.parseToken(token);
            Long userId = claims.get("userId", Long.class);
            String role = claims.get("role", String.class);
            if (userId == null || role == null) {
                authResponseWriter.write(response, ErrorCode.UNAUTHORIZED, "无效的令牌");
                return false;
            }
            String tokenId = claims.getId();
            if (tokenId != null && tokenBlacklistService.isBlacklisted(tokenId)) {
                authResponseWriter.write(response, ErrorCode.UNAUTHORIZED, "登录已失效，请重新登录");
                return false;
            }
            LoginUser loginUser = new LoginUser(userId, claims.getSubject(), role);
            UserContext.set(loginUser);
            request.setAttribute(LoginUser.REQUEST_ATTRIBUTE, loginUser);
            request.setAttribute(RequestKeys.TOKEN_ID, tokenId);
            request.setAttribute(RequestKeys.TOKEN_EXPIRE_SECONDS, jwtUtil.remainingSeconds(claims));
            return true;
        } catch (ExpiredJwtException e) {
            authResponseWriter.write(response, ErrorCode.UNAUTHORIZED, "登录已过期，请重新登录");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            log.warn("JWT 校验失败: {}", e.getMessage());
            authResponseWriter.write(response, ErrorCode.UNAUTHORIZED, "无效的令牌");
            return false;
        }
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        UserContext.clear();
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header == null || header.isBlank()) {
            return null;
        }
        String token = header.startsWith(BEARER_PREFIX) ? header.substring(BEARER_PREFIX.length()) : header;
        return token.isBlank() ? null : token.trim();
    }
}