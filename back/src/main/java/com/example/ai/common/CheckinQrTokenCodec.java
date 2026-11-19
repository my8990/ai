package com.example.ai.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

@Component
public class CheckinQrTokenCodec {

    private final String secret;

    private final long bucketSeconds;

    public CheckinQrTokenCodec(@Value("${app.checkin.qr-secret}") String secret,
                               @Value("${app.checkin.qr-bucket-seconds:300}")
                               long bucketSeconds) {
        this.secret = secret;
        this.bucketSeconds = bucketSeconds <= 0 ? 300 : bucketSeconds;
    }

    public String mint(Long checkinId, String code) {
        if (checkinId == null || code == null) {
            return null;
        }
        return sign(checkinId, code, currentBucket());
    }

    public boolean verify(Long checkinId, String code, String token) {
        if (checkinId == null || code == null || token == null || token.isBlank()) {
            return false;
        }
        long bucket = currentBucket();
        return safeEquals(token, sign(checkinId, code, bucket))
                || safeEquals(token, sign(checkinId, code, bucket - 1));
    }

    private long currentBucket() {
        return System.currentTimeMillis() / 1000 / bucketSeconds;
    }

    private String sign(Long checkinId, String code, long bucket) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal((checkinId + "|" + code + "|" + bucket).getBytes(StandardCharsets.UTF_8));
            byte[] truncated = new byte[16];
            System.arraycopy(raw, 0, truncated, 0, truncated.length);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(truncated);
        } catch (Exception e) {
            throw new IllegalStateException("签到二维码令牌生成失败", e);
        }
    }

    private boolean safeEquals(String a, String b) {
        return MessageDigest.isEqual(a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}