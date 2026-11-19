package com.example.ai.security;

import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Slf4j
@Component
public class FileUrlSigner {

    private static final String FILE_SEGMENT = "/files/";

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    @Value("${file.signature.enabled:true}")
    private boolean enabled;

    @Value("${file.signature.secret:}")
    private String signSecret;

    @Value("${jwt.secret:}")
    private String jwtSecret;

    @Value("${file.signature.expires-seconds:600}")
    private long expiresSeconds;

    @Value("${file.signature.max-expires-seconds:86400}")
    private long maxExpiresSeconds;

    @Value("${file.access-url:http://localhost:8080/api/files}")
    private String accessUrl;

    private String resolvedSecret;

    @PostConstruct
    void init() {
        if (signSecret != null && !signSecret.isBlank()) {
            resolvedSecret = signSecret;
            return;
        }
        resolvedSecret = jwtSecret == null ? "" : jwtSecret;
        if (enabled) {
            log.warn("file.signature.secret 未配置，回退使用 jwt.secret 作为文件签名密钥");
        }
    }

    public boolean isEnabled() {
        return enabled;
    }

    public String sign(String rawUrlOrName) {
        String fileName = fileNameOf(rawUrlOrName);
        if (!enabled || fileName.isEmpty()) {
            return rawUrlOrName;
        }
        long expires = System.currentTimeMillis() / 1000 + Math.max(1, expiresSeconds);
        String signature = hmac(fileName + "|" + expires);
        return baseUrl() + "/" + fileName + "?expires=" + expires + "&sig=" + signature;
    }

    public String signName(String fileName) {
        return sign(fileName);
    }

    public String normalize(String rawUrlOrName) {
        String fileName = fileNameOf(rawUrlOrName);
        if (fileName.isEmpty()) {
            return rawUrlOrName;
        }
        return baseUrl() + "/" + fileName;
    }

    public boolean verify(String fileName, String expiresParam, String signature) {
        if (!enabled) {
            return true;
        }
        if (fileName == null || fileName.isBlank() || expiresParam == null || signature == null) {
            return false;
        }
        long expires;
        try {
            expires = Long.parseLong(expiresParam.trim());
        } catch (NumberFormatException e) {
            return false;
        }
        long now = System.currentTimeMillis() / 1000;
        if (expires < now) {
            return false;
        }
        if (maxExpiresSeconds > 0 && expires > now + maxExpiresSeconds) {
            return false;
        }
        String expected = hmac(fileName + "|" + expires);
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                signature.getBytes(StandardCharsets.UTF_8));
    }

    public String fileNameOf(String rawUrlOrName) {
        if (rawUrlOrName == null) {
            return "";
        }
        String url = rawUrlOrName.trim();
        int query = url.indexOf('?');
        if (query >= 0) {
            url = url.substring(0, query);
        }
        int fragment = url.indexOf('#');
        if (fragment >= 0) {
            url = url.substring(0, fragment);
        }
        String base = baseUrl();
        if (url.startsWith(base + "/")) {
            return url.substring(base.length() + 1);
        }
        int filesIndex = url.indexOf(FILE_SEGMENT);
        if (filesIndex >= 0) {
            return url.substring(filesIndex + FILE_SEGMENT.length());
        }
        if (url.contains("://")) {
            return "";
        }
        return url;
    }

    private String baseUrl() {
        String base = accessUrl == null ? "" : accessUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        return base;
    }

    private String hmac(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret().getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            byte[] raw = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            byte[] truncated = new byte[16];
            System.arraycopy(raw, 0, truncated, 0, truncated.length);
            return URLEncoder.encode(
                    Base64.getUrlEncoder().withoutPadding().encodeToString(truncated),
                    StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("文件签名生成失败", e);
        }
    }

    private String secret() {
        return resolvedSecret == null ? "" : resolvedSecret;
    }
}