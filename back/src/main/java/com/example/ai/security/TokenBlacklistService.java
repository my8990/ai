package com.example.ai.security;

import com.example.ai.common.BizException;
import com.example.ai.common.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class TokenBlacklistService {

    private static final String KEY_PREFIX = "auth:token:blacklist:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.redis-fail-closed:true}")
    private boolean failClosed;

    public void blacklist(String tokenId, long expireSeconds) {
        if (tokenId == null || tokenId.isBlank() || expireSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue()
                    .set(KEY_PREFIX + tokenId, "1", Duration.ofSeconds(expireSeconds));
        } catch (Exception e) {
            log.error("令牌写入黑名单失败: tokenId={}", tokenId, e);
        }
    }

    public boolean isBlacklisted(String tokenId) {
        if (tokenId == null || tokenId.isBlank()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(KEY_PREFIX + tokenId));
        } catch (Exception e) {
            if (failClosed) {
                log.error("查询令牌黑名单失败，按拒绝处理(fail-closed): tokenId={}", tokenId, e);
                throw new BizException(ErrorCode.SERVICE_UNAVAILABLE, "鉴权服务暂不可用，请稍后重试");
            }
            log.warn("查询令牌黑名单失败，按放行处理(fail-open): {}", e.getMessage());
            return false;
        }
    }
}