package com.example.ai.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
public class LoginAttemptService {

    private static final String FAIL_KEY_PREFIX = "auth:login:fail:";

    private static final String LOCK_KEY_PREFIX = "auth:login:lock:";

    private final StringRedisTemplate redisTemplate;

    @Value("${app.security.login.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.security.login.window-minutes:15}")
    private long windowMinutes;

    @Value("${app.security.login.lock-minutes:15}")
    private long lockMinutes;

    public long lockRemainingSeconds(String account) {
        try {
            Long ttl = redisTemplate.getExpire(LOCK_KEY_PREFIX + key(account));
            return ttl == null || ttl < 0 ? 0L : ttl;
        } catch (Exception e) {
            log.warn("读取登录锁定状态失败: {}", e.getMessage());
            return 0L;
        }
    }

    public long onFailure(String account) {
        String accountKey = key(account);
        String failKey = FAIL_KEY_PREFIX + accountKey;
        try {
            Long count = redisTemplate.opsForValue().increment(failKey);
            long failures = count == null ? 0L : count;
            if (failures == 1L) {
                redisTemplate.expire(failKey, Duration.ofMinutes(windowMinutes));
            }
            if (failures >= maxAttempts) {
                redisTemplate.opsForValue()
                        .set(LOCK_KEY_PREFIX + accountKey, "1", Duration.ofMinutes(lockMinutes));
                redisTemplate.delete(failKey);
                log.warn("账号连续登录失败被临时锁定: account={}, failures={}", accountKey, failures);
                return 0L;
            }
            return Math.max(0L, maxAttempts - failures);
        } catch (Exception e) {
            log.warn("记录登录失败次数失败: {}", e.getMessage());
            return 0L;
        }
    }

    public void onSuccess(String account) {
        String accountKey = key(account);
        try {
            redisTemplate.delete(FAIL_KEY_PREFIX + accountKey);
            redisTemplate.delete(LOCK_KEY_PREFIX + accountKey);
        } catch (Exception e) {
            log.warn("清理登录失败计数失败: {}", e.getMessage());
        }
    }

    private String key(String account) {
        return account == null ? "" : account.trim().toLowerCase(Locale.ROOT);
    }
}