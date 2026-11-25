package com.tecsup.palacetec.service;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.UUID;

/** uuid5 de Python: UUID determinista (versión 5, SHA-1) a partir de otro y de un nombre. */
final class NameUuid {

    private static final int VERSION_BYTE = 6;
    private static final int VARIANT_BYTE = 8;

    private NameUuid() {
    }

    static UUID version5(UUID namespace, String name) {
        byte[] hash = sha1(ByteBuffer.allocate(16).putLong(namespace.getMostSignificantBits())
                .putLong(namespace.getLeastSignificantBits()).array(), name.getBytes(StandardCharsets.UTF_8));
        hash[VERSION_BYTE] = (byte) (hash[VERSION_BYTE] & 0x0f | 0x50);
        hash[VARIANT_BYTE] = (byte) (hash[VARIANT_BYTE] & 0x3f | 0x80);
        ByteBuffer bytes = ByteBuffer.wrap(hash);
        return new UUID(bytes.getLong(), bytes.getLong());
    }

    private static byte[] sha1(byte[] namespace, byte[] name) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-1");
            digest.update(namespace);
            return digest.digest(name);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
