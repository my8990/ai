package com.example.ai.security;

import com.example.ai.common.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuthorizationInterceptor implements HandlerInterceptor {

    private final AuthResponseWriter authResponseWriter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }

        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole == null) {
            return true;
        }

        LoginUser loginUser = UserContext.getOrNull();
        if (loginUser == null) {
            authResponseWriter.write(response, ErrorCode.UNAUTHORIZED, "未登录，请先登录");
            return false;
        }

        for (String allowed : requireRole.value()) {
            if (allowed.equalsIgnoreCase(loginUser.role())) {
                return true;
            }
        }

        authResponseWriter.write(response, ErrorCode.FORBIDDEN, "无权访问该接口");
        return false;
    }
}