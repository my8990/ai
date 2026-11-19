package com.example.ai.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CheckinQrTokenCodecTest {

    private static final String SECRET = "TestCheckinQrSecretKeyAtLeast16Bytes";

    private final CheckinQrTokenCodec codec = new CheckinQrTokenCodec(SECRET, 300);

    @Test
    @DisplayName("正常签发的令牌可以通过校验")
    void mintAndVerifySuccess() {
        String token = codec.mint(100L, "A1B2C3");

        assertThat(token).isNotNull();
        assertThat(codec.verify(100L, "A1B2C3", token)).isTrue();
    }

    @Test
    @DisplayName("篡改签到码后令牌失效，防止换码复用")
    void tamperedCodeRejected() {
        String token = codec.mint(100L, "A1B2C3");

        assertThat(codec.verify(100L, "A1B2C4", token)).isFalse();
    }

    @Test
    @DisplayName("令牌与签到ID绑定，跨签到复用无效")
    void tokenBoundToCheckinId() {
        String token = codec.mint(100L, "A1B2C3");

        assertThat(codec.verify(200L, "A1B2C3", token)).isFalse();
    }

    @Test
    @DisplayName("空值或空令牌一律拒绝")
    void nullAndBlankRejected() {
        assertThat(codec.mint(null, "A1B2C3")).isNull();
        assertThat(codec.mint(100L, null)).isNull();
        assertThat(codec.verify(100L, "A1B2C3", null)).isFalse();
        assertThat(codec.verify(100L, "A1B2C3", "   ")).isFalse();
    }

    @Test
    @DisplayName("不同密钥签发的令牌不可互认")
    void differentSecretRejected() {
        CheckinQrTokenCodec other = new CheckinQrTokenCodec("AnotherCheckinQrSecretKey16Bytes", 300);
        String token = other.mint(100L, "A1B2C3");

        assertThat(codec.verify(100L, "A1B2C3", token)).isFalse();
    }
}