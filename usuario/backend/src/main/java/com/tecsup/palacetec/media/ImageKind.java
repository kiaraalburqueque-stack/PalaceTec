package com.tecsup.palacetec.media;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Formatos de imagen que se aceptan, reconocidos por su firma y no solo por la extensión. */
enum ImageKind {

    JPEG("image/jpeg", "jpeg") {
        @Override
        boolean matches(byte[] content) {
            return startsWith(content, 0, 0xFF, 0xD8, 0xFF);
        }
    },
    PNG("image/png", "png") {
        @Override
        boolean matches(byte[] content) {
            return startsWith(content, 0, 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n');
        }
    },
    WEBP("image/webp", "webp") {
        @Override
        boolean matches(byte[] content) {
            return hasText(content, 0, "RIFF") && hasText(content, 8, "WEBP");
        }
    };

    private final String mime;
    private final String format;

    ImageKind(String mime, String format) {
        this.mime = mime;
        this.format = format;
    }

    abstract boolean matches(byte[] content);

    /** Nombre del formato en ImageIO. */
    String format() {
        return format;
    }

    static boolean isAllowedMime(String contentType) {
        String mime = contentType == null ? "" : contentType.split(";")[0].strip().toLowerCase(Locale.ROOT);
        return Arrays.stream(values()).anyMatch(kind -> kind.mime.equals(mime));
    }

    static Optional<ImageKind> ofFile(String name) {
        if (name == null || !name.contains(".")) {
            return Optional.empty();
        }
        return switch (name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT)) {
            case "jpg", "jpeg" -> Optional.of(JPEG);
            case "png" -> Optional.of(PNG);
            case "webp" -> Optional.of(WEBP);
            default -> Optional.empty();
        };
    }

    static boolean hasText(byte[] content, int offset, String text) {
        byte[] expected = text.getBytes(StandardCharsets.US_ASCII);
        return offset + expected.length <= content.length
                && Arrays.equals(content, offset, offset + expected.length, expected, 0, expected.length);
    }

    private static boolean startsWith(byte[] content, int offset, int... expected) {
        for (int i = 0; i < expected.length; i++) {
            if (offset + i >= content.length || (content[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
