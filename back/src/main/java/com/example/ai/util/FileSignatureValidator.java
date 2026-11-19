package com.example.ai.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

public final class FileSignatureValidator {

    private static final int HEADER_LENGTH = 16;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};

    private static final byte[] PNG = {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};

    private static final byte[] GIF = {0x47, 0x49, 0x46, 0x38};

    private static final byte[] BMP = {0x42, 0x4D};

    private static final byte[] RIFF = {0x52, 0x49, 0x46, 0x46};

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46};

    private static final byte[] ZIP = {0x50, 0x4B, 0x03, 0x04};

    private static final byte[] ZIP_EMPTY = {0x50, 0x4B, 0x05, 0x06};

    private static final byte[] ZIP_SPANNED = {0x50, 0x4B, 0x07, 0x08};

    private static final byte[] OLE2 = {
            (byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, (byte) 0xA1, (byte) 0xB1, 0x1A, (byte) 0xE1};

    private static final byte[] RAR = {0x52, 0x61, 0x72, 0x21, 0x1A, 0x07};

    private static final byte[] SEVEN_ZIP = {0x37, 0x7A, (byte) 0xBC, (byte) 0xAF, 0x27, 0x1C};

    private static final byte[] RTF = {0x7B, 0x5C, 0x72, 0x74, 0x66};

    private static final String WEBP_MARK = "WEBP";

    private FileSignatureValidator() {
    }

    public static boolean matches(String extension, byte[] header) {
        String ext = extension == null ? "" : extension.toLowerCase(Locale.ROOT);
        if (header == null || header.length == 0) {
            return false;
        }
        return switch (ext) {
            case ".jpg", ".jpeg" -> startsWith(header, JPEG);
            case ".png" -> startsWith(header, PNG);
            case ".gif" -> startsWith(header, GIF);
            case ".bmp" -> startsWith(header, BMP);
            case ".webp" -> isWebp(header);
            case ".pdf" -> startsWith(header, PDF);
            case ".docx", ".xlsx", ".pptx", ".zip", ".odp", ".odt" -> startsWith(header, ZIP)
                    || startsWith(header, ZIP_EMPTY) || startsWith(header, ZIP_SPANNED);
            case ".doc", ".xls", ".ppt" -> startsWith(header, OLE2);
            case ".rtf" -> startsWith(header, RTF);
            case ".rar" -> startsWith(header, RAR);
            case ".7z" -> startsWith(header, SEVEN_ZIP);
            case ".txt" -> true;
            default -> false;
        };
    }

    public static byte[] readHeader(InputStream in) throws IOException {
        byte[] buffer = new byte[HEADER_LENGTH];
        int read = 0;
        while (read < buffer.length) {
            int count = in.read(buffer, read, buffer.length - read);
            if (count <= 0) {
                break;
            }
            read += count;
        }
        if (read == buffer.length) {
            return buffer;
        }
        byte[] shorter = new byte[read];
        System.arraycopy(buffer, 0, shorter, 0, read);
        return shorter;
    }

    private static boolean isWebp(byte[] header) {
        if (!startsWith(header, RIFF) || header.length < 12) {
            return false;
        }
        String mark = new String(new byte[]{header[8], header[9], header[10], header[11]},
                java.nio.charset.StandardCharsets.US_ASCII);
        return WEBP_MARK.equals(mark);
    }

    private static boolean startsWith(byte[] header, byte[] magic) {
        if (header.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}