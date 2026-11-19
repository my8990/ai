package com.example.ai.audit;

import com.example.ai.common.RequestKeys;
import com.example.ai.entity.AuditLog;
import com.example.ai.security.LoginUser;
import com.example.ai.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@RequiredArgsConstructor
public class AuditLogInterceptor implements HandlerInterceptor {

    private static final Logger auditLog = LoggerFactory.getLogger("AUDIT");

    private static final String ATTR_ACTION = "com.example.ai.auditAction";

    private static final int ERROR_STATUS_THRESHOLD = 400;

    private final AuditWriter auditWriter;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        AuditAction action = handlerMethod.getMethodAnnotation(AuditAction.class);
        if (action == null) {
            return true;
        }
        request.setAttribute(ATTR_ACTION, action);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        Object attr = request.getAttribute(ATTR_ACTION);
        if (!(attr instanceof AuditAction action)) {
            return;
        }

        AuditLog record = new AuditLog();
        LoginUser loginUser = (LoginUser) request.getAttribute(LoginUser.REQUEST_ATTRIBUTE);
        if (loginUser != null) {
            record.setUserId(loginUser.id());
            record.setUsername(loginUser.username());
            record.setRole(loginUser.role());
        }
        record.setModule(action.module());
        record.setAction(action.action());
        record.setMethod(request.getMethod());
        record.setUri(request.getRequestURI());
        record.setHttpStatus(response.getStatus());
        record.setSuccess(response.getStatus() < ERROR_STATUS_THRESHOLD ? 1 : 0);
        record.setErrorMessage(errorMessage(request));
        record.setIp(IpUtil.resolve(request));
        record.setDurationMs(System.currentTimeMillis() - AccessLogInterceptor.startTime(request));
        record.setTraceId((String) request.getAttribute(RequestKeys.TRACE_ID));

        auditLog.info("module={} action={} user={}({}) role={} {} {} -> {} success={} error={}",
                record.getModule(),
                record.getAction(),
                record.getUsername() == null ? "-" : record.getUsername(),
                record.getUserId() == null ? "-" : record.getUserId(),
                record.getRole() == null ? "-" : record.getRole(),
                record.getMethod(),
                record.getUri(),
                record.getHttpStatus(),
                record.getSuccess(),
                record.getErrorMessage() == null ? "-" : record.getErrorMessage());

        auditWriter.save(record);
    }

    private String errorMessage(HttpServletRequest request) {
        Object message = request.getAttribute(RequestKeys.ERROR_MESSAGE);
        if (message == null) {
            return null;
        }
        String text = message.toString();
        return text.length() > 500 ? text.substring(0, 500) : text;
    }
}