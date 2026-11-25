package com.tecsup.palacetec.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder.BCryptVersion;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * PASSWORD_HASHERS de Django. Escribe como BCryptSHA256PasswordHasher, {@code bcrypt_sha256$$2b$12$...}, donde bcrypt
 * recibe el SHA-256 del password en hexadecimal (64 caracteres, así nunca se trunca a 72 bytes). También acepta los
 * hashes {@code pbkdf2_sha256$...} de cuentas antiguas.
 */
@Component
public class DjangoPasswordEncoder implements PasswordEncoder {

    private static final String PREFIX = "bcrypt_sha256$";
    private static final String PBKDF2_PREFIX = "pbkdf2_sha256$";
    private static final int ROUNDS = 12;

    private final BCryptPasswordEncoder bcrypt = new BCryptPasswordEncoder(BCryptVersion.$2B, ROUNDS);

    @Override
    public String encode(CharSequence rawPassword) {
        return PREFIX + bcrypt.encode(sha256Hex(rawPassword));
    }

    @Override
    public boolean matches(CharSequence rawPassword, String encodedPassword) {
        if (encodedPassword == null) {
            return false;
        }
        if (encodedPassword.startsWith(PBKDF2_PREFIX)) {
            return matchesPbkdf2(rawPassword, encodedPassword);
        }
        return encodedPassword.startsWith(PREFIX)
                && bcrypt.matches(sha256Hex(rawPassword), encodedPassword.substring(PREFIX.length()));
    }

    /** Django vuelve a guardar la contraseña al comprobarla si su hash no usa el algoritmo y el costo actuales. */
    @Override
    public boolean upgradeEncoding(String encodedPassword) {
        return !encodedPassword.matches("bcrypt_sha256\\$\\$2[aby]\\$" + ROUNDS + "\\$.*");
    }

    private static boolean matchesPbkdf2(CharSequence rawPassword, String encodedPassword) {
        String[] parts = encodedPassword.split("\\$", 4);
        try {
            byte[] hash = pbkdf2(rawPassword, parts[2], Integer.parseInt(parts[1]));
            return MessageDigest.isEqual(hash, Base64.getDecoder().decode(parts[3]));
        } catch (ArrayIndexOutOfBoundsException | IllegalArgumentException exception) {
            return false;
        }
    }

    /**
     * Un bloque de PBKDF2-HMAC-SHA256. No se usa PBEKeySpec porque Java no acepta contraseñas en UTF-8 fuera de ASCII
     * igual que Python.
     */
    private static byte[] pbkdf2(CharSequence password, String salt, int iterations) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(password.toString().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(salt.getBytes(StandardCharsets.UTF_8));
            byte[] block = mac.doFinal(new byte[] {0, 0, 0, 1});
            byte[] result = block.clone();
            for (int i = 1; i < iterations; i++) {
                block = mac.doFinal(block);
                for (int j = 0; j < result.length; j++) {
                    result[j] ^= block[j];
                }
            }
            return result;
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String sha256Hex(CharSequence value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
