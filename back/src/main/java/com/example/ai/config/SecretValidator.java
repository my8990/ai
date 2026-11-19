package com.example.ai.config;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class SecretValidator {

    private static final int MIN_JWT_SECRET_BYTES = 32;

    private static final int MIN_QR_SECRET_BYTES = 16;

    @Value("${app.security.strict-config:false}")
    private boolean strictConfig;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${app.checkin.qr-secret:}")
    private String checkinQrSecret;

    @Value("${spring.datasource.password:}")
    private String datasourcePassword;

    @Value("${wechat.appid:}")
    private String wechatAppId;

    @Value("${wechat.secret:}")
    private String wechatSecret;

    @Value("${ai.api-key:}")
    private String aiApiKey;

    @Value("${amap.key:}")
    private String amapKey;

    @Value("${file.signature.enabled:true}")
    private boolean fileSignatureEnabled;

    @Value("${file.signature.secret:}")
    private String fileSignSecret;

    @PostConstruct
    public void validate() {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        validateJwtSecret(errors);
        validateCheckinSecret(errors);

        if (fileSignatureEnabled && isBlank(fileSignSecret)) {
            warnings.add("file.signature.secret 未配置：文件签名将回退使用 jwt.secret，建议设置独立的 FILE_SIGN_SECRET");
        }

        if (isBlank(datasourcePassword)) {
            warnings.add("spring.datasource.password 为空：若数据库需要密码，请设置环境变量 DB_PASSWORD");
        }

        if (isBlank(wechatAppId) || isBlank(wechatSecret)) {
            addIssue(errors, warnings, "微信 appid/secret 未配置：微信登录与课堂小程序码将不可用，请设置 WECHAT_APPID / WECHAT_SECRET");
        }

        if (isBlank(amapKey)) {
            addIssue(errors, warnings, "高德地图 key 未配置：签到定位与逆地理将不可用，请设置 AMAP_KEY");
        }

        if (isBlank(aiApiKey)) {
            addIssue(errors, warnings, "AI api-key 未配置：AI 助手将返回未配置提示，请设置 AI_API_KEY");
        }

        for (String warning : warnings) {
            log.warn("配置检查 - {}", warning);
        }

        if (!errors.isEmpty()) {
            throw new IllegalStateException("启动配置校验失败，请补全以下配置后重试:\n  - "
                    + String.join("\n  - ", errors));
        }
    }

    private void validateJwtSecret(List<String> errors) {
        if (isBlank(jwtSecret)) {
            errors.add("jwt.secret 未配置：请设置环境变量 JWT_SECRET，或在 application-local.yml 中配置");
            return;
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < MIN_JWT_SECRET_BYTES) {
            errors.add("jwt.secret 至少需要 " + MIN_JWT_SECRET_BYTES + " 字节（HS256 要求），当前仅 "
                    + jwtSecret.getBytes(StandardCharsets.UTF_8).length + " 字节");
        }
    }

    private void validateCheckinSecret(List<String> errors) {
        if (isBlank(checkinQrSecret)) {
            errors.add("app.checkin.qr-secret 未配置：签到二维码签名必须使用独立密钥，请设置环境变量 CHECKIN_QR_SECRET");
            return;
        }
        if (checkinQrSecret.getBytes(StandardCharsets.UTF_8).length < MIN_QR_SECRET_BYTES) {
            errors.add("app.checkin.qr-secret 至少需要 " + MIN_QR_SECRET_BYTES + " 字节，当前仅 "
                    + checkinQrSecret.getBytes(StandardCharsets.UTF_8).length + " 字节");
        }
    }

    private void addIssue(List<String> errors, List<String> warnings, String message) {
        if (strictConfig) {
            errors.add(message);
        } else {
            warnings.add(message);
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}