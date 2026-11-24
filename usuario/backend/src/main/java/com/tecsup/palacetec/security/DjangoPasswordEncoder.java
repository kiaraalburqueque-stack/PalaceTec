package com.tecsup.palacetec.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Mismo formato que BCryptSHA256PasswordHasher de Django: {@code bcrypt_sha256$$2b$12$...}, donde bcrypt recibe
 * el SHA-256 del password en hexadecimal (64 caracteres, así nunca se trunca a 72 bytes).
 */
@Component
public class DjangoPasswordEncoder implements PasswordEncoder {

    private static final String PREFIX = "bcrypt_sha256$";
    private static final int ROUNDS = 12;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(BCryptVersion.$2B, ROUNDS);

    @Override
    public String encode(CharSequence rawPassword) {
        return PREFIX + bcrypt.encode(sha256Hex(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        return encodedPassword != null && encodedPassword.startsWith(PREFIX)
                && bcrypt.matches(sha256Hex(rawPassword), encodedPassword.substring(PREFIX.length()));
    }

    private static String sha256Hex(CharSequence value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
