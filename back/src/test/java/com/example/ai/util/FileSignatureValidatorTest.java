package com.example.ai.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FileSignatureValidatorTest {

    private static final byte[] PNG_HEADER = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final byte[] JPEG_HEADER = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0};

    private static final byte[] ZIP_HEADER = {0x50, 0x4B, 0x03, 0x04};

    private static final byte[] RAR_HEADER = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07};

    private static final byte[] PDF_HEADER = {0x25, 0x50, 0x44, 0x46};

    @Test
    @DisplayName("真实文件头与扩展名匹配时通过校验")
    void matchingSignatureAccepted() {
        assertThat(FileSignatureValidator.matches(".png", PNG_HEADER)).isTrue();
        assertThat(FileSignatureValidator.matches(".jpg", JPEG_HEADER)).isTrue();
        assertThat(FileSignatureValidator.matches(".docx", ZIP_HEADER)).isTrue();
        assertThat(FileSignatureValidator.matches(".rar", RAR_HEADER)).isTrue();
        assertThat(FileSignatureValidator.matches(".pdf", PDF_HEADER)).isTrue();
    }

    @Test
    @DisplayName("伪装扩展名的文件被拒绝，阻断上传木马")
    void disguisedExtensionRejected() {
        assertThat(FileSignatureValidator.matches(".png", JPEG_HEADER)).isFalse();
        assertThat(FileSignatureValidator.matches(".pdf", ZIP_HEADER)).isFalse();
        assertThat(FileSignatureValidator.matches(".jpg", PNG_HEADER)).isFalse();
    }

    @Test
    @DisplayName("未在白名单中的扩展名一律拒绝")
    void unknownExtensionRejected() {
        assertThat(FileSignatureValidator.matches(".exe", ZIP_HEADER)).isFalse();
        assertThat(FileSignatureValidator.matches(null, PNG_HEADER)).isFalse();
        assertThat(FileSignatureValidator.matches("", PNG_HEADER)).isFalse();
    }

    @Test
    @DisplayName("空文件头拒绝，避免空文件绕过校验")
    void emptyHeaderRejected() {
        assertThat(FileSignatureValidator.matches(".png", null)).isFalse();
        assertThat(FileSignatureValidator.matches(".png", new byte[0])).isFalse();
    }

    @Test
    @DisplayName("扩展名大小写不敏感")
    void extensionCaseInsensitive() {
        assertThat(FileSignatureValidator.matches(".PNG", PNG_HEADER)).isTrue();
        assertThat(FileSignatureValidator.matches(".PDF", PDF_HEADER)).isTrue();
    }
}