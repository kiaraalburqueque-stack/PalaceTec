package com.tecsup.palacetec.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/** Tokens de los enlaces enviados por correo. En la base solo se guarda su SHA-256, igual que en Django. */
public final class OneTimeTokens {

    private static final SecureRandom RANDOM = new SecureRandom();
    /** 24 bytes son 32 caracteres: el enlace cabe en una línea del correo. */
    private static final int BYTES = 24;

    private OneTimeTokens() {
    }

    public static String create() {
        byte[] bytes = new byte[BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
