package com.example.ai.audit;

import com.example.ai.common.RequestKeys;
import com.example.ai.security.LoginUser;
import com.example.ai.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.AsyncHandlerInterceptor;

import java.util.UUID;

@Component
public class AccessLogInterceptor implements AsyncHandlerInterceptor {

    private static final Logger accessLog = LoggerFactory.getLogger("ACCESS");

    private static final String TRACE_ID_KEY = "traceId";

    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String traceId = (String) request.getAttribute(RequestKeys.TRACE_ID);
        if (traceId == null) {
            traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            request.setAttribute(RequestKeys.TRACE_ID, traceId);
            request.setAttribute(RequestKeys.START_TIME, System.currentTimeMillis());
        }
        MDC.put(TRACE_ID_KEY, traceId);
        response.setHeader(TRACE_ID_HEADER, traceId);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        long start = startTime(request);
        long cost = System.currentTimeMillis() - start;
        LoginUser loginUser = (LoginUser) request.getAttribute(LoginUser.REQUEST_ATTRIBUTE);
        accessLog.info("{} {} -> {} {}ms user={} role={} ip={}",
                request.getMethod(),
                request.getRequestURI(),
                response.getStatus(),
                cost,
                loginUser == null ? "-" : loginUser.id(),
                loginUser == null ? "-" : loginUser.role(),
                IpUtil.resolve(request));
        MDC.remove(TRACE_ID_KEY);
    }

    @Override
    public void afterConcurrentHandlingStarted(HttpServletRequest request, HttpServletResponse response, Object handler) {
        MDC.remove(TRACE_ID_KEY);
    }

    static long startTime(HttpServletRequest request) {
        Object start = request.getAttribute(RequestKeys.START_TIME);
        return start instanceof Long value ? value : System.currentTimeMillis();
    }
}