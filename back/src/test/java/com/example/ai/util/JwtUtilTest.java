package com.example.ai.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtUtilTest {

    private static final String SECRET = "UnitTestJwtSecretKeyMustBeAtLeast32BytesLong!";

    private JwtUtil jwtUtil;

    @BeforeEach
    void setUp() {
        jwtUtil = new JwtUtil();
        ReflectionTestUtils.setField(jwtUtil, "secret", SECRET);
        ReflectionTestUtils.setField(jwtUtil, "expiration", 3600_000L);
    }

    @Test
    @DisplayName("生成的令牌可解析出用户ID、用户名与角色")
    void generateAndParse() {
        String token = jwtUtil.generateToken(7L, "alice", "student");
        Claims claims = jwtUtil.parseToken(token);

        assertThat(claims.get("userId", Long.class)).isEqualTo(7L);
        assertThat(claims.get("role", String.class)).isEqualTo("student");
        assertThat(claims.getSubject()).isEqualTo("alice");
        assertThat(claims.getId()).isNotBlank();
    }

    @Test
    @DisplayName("每次签发令牌ID唯一，注销黑名单不会误伤其他会话")
    void tokenIdIsUnique() {
        String first = jwtUtil.generateToken(7L, "alice", "student");
        String second = jwtUtil.generateToken(7L, "alice", "student");

        String firstId = jwtUtil.parseToken(first).getId();
        String secondId = jwtUtil.parseToken(second).getId();

        assertThat(firstId).isNotEqualTo(secondId);
    }

    @Test
    @DisplayName("剩余有效期计算正确且不为负")
    void remainingSeconds() {
        Claims claims = jwtUtil.parseToken(jwtUtil.generateToken(7L, "alice", "student"));

        assertThat(jwtUtil.remainingSeconds(claims)).isBetween(0L, 3600L);
    }

    @Test
    @DisplayName("被篡改的令牌解析失败")
    void tamperedTokenRejected() {
        String token = jwtUtil.generateToken(7L, "alice", "student");
        String[] parts = token.split("\\.");
        char first = parts[1].charAt(0);
        parts[1] = (first == 'A' ? 'B' : 'A') + parts[1].substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + parts[2];

        assertThatThrownBy(() -> jwtUtil.parseToken(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    @DisplayName("使用其他密钥签发的令牌无法通过校验")
    void foreignSecretRejected() {
        JwtUtil other = new JwtUtil();
        ReflectionTestUtils.setField(other, "secret", "AnotherUnitTestJwtSecretKeyAtLeast32Bytes!!");
        ReflectionTestUtils.setField(other, "expiration", 3600_000L);

        String foreignToken = other.generateToken(7L, "alice", "student");

        assertThatThrownBy(() -> jwtUtil.parseToken(foreignToken)).isInstanceOf(JwtException.class);
    }
}