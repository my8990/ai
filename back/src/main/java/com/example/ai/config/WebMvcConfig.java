package com.example.ai.config;

import com.example.ai.audit.AccessLogInterceptor;
import com.example.ai.audit.AuditLogInterceptor;
import com.example.ai.security.AuthorizationInterceptor;
import com.example.ai.security.CurrentUserArgumentResolver;
import com.example.ai.security.JwtInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final JwtInterceptor jwtInterceptor;
    private final AuthorizationInterceptor authorizationInterceptor;
    private final CurrentUserArgumentResolver currentUserArgumentResolver;
    private final AccessLogInterceptor accessLogInterceptor;
    private final AuditLogInterceptor auditLogInterceptor;
    private final FileAccessInterceptor fileAccessInterceptor;

    private static final String[] STATIC_PATHS = {
            "/doc.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/webjars/**",
            "/favicon.ico",
            "/files/**"
    };

    @Value("${file.upload-dir:uploads}")
    private String uploadDir;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(accessLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(STATIC_PATHS)
                .excludePathPatterns("/ws/**")
                .order(-1);

        registry.addInterceptor(auditLogInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(STATIC_PATHS)
                .excludePathPatterns("/ws/**")
                .order(-1);

        registry.addInterceptor(jwtInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/login",
                        "/user/register",
                        "/user/wx-login",
                        "/ai/ping",
                        "/actuator/health/**",
                        "/actuator/info",
                        "/doc.html",
                        "/swagger-ui/**",
                        "/v3/api-docs/**",
                        "/webjars/**",
                        "/favicon.ico",
                        "/files/**"
                )
                .excludePathPatterns("/ws/**")
                .order(0);

        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/ws/**")
                .order(1);

        registry.addInterceptor(fileAccessInterceptor)
                .addPathPatterns("/files/**")
                .order(2);
    }

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
        resolvers.add(currentUserArgumentResolver);
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:" + uploadDir + "/");
    }
}