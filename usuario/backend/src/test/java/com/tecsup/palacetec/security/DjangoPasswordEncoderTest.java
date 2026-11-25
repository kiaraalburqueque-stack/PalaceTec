package com.tecsup.palacetec.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class DjangoPasswordEncoderTest {

    private final DjangoPasswordEncoder encoder = new DjangoPasswordEncoder();

    /** Hashes generados con django.contrib.auth.hashers.make_password (Django 6.1.2) sobre claves ficticias. */
    static Stream<Arguments> djangoHashes() {
        return Stream.of(
                Arguments.of("Prueba#2026",
                        "bcrypt_sha256$$2b$12$xqw0H0kJNTqMpgUrd6Y6FOZopA3BLpbCQTZ53WN.GDMLmdx4IjWSG"),
                Arguments.of("Contraseña Ñandú €1",
                        "bcrypt_sha256$$2b$12$LMVB1iQkQoWkCjwF3fomVurJG5B1uTUqE8hrOGhUzD1/./t3I7gIa"),
                // 100 caracteres: sin el SHA-256 previo, bcrypt la truncaría a 72 bytes.
                Arguments.of("A!" + "x".repeat(98),
                        "bcrypt_sha256$$2b$12$RUdi1p8NkHTP0Vf.nBi98.BYqSbKSDf.TVGz/Vn99nvkBfxpSAC4S"),
                // PBKDF2PasswordHasher, el segundo de PASSWORD_HASHERS, con pocas iteraciones.
                Arguments.of("Prueba#2026", "pbkdf2_sha256$1000$saltDePrueba0123456789"
                        + "$NkHZaK7NFAEGtfl25B2aXv1jvKPCMStMkFnRm+s3a3Y="),
                Arguments.of("Contraseña Ñandú €1", "pbkdf2_sha256$1000$saltDePrueba0123456789"
                        + "$z0zmY0YjfnsvcO352NflVXf6+9A+QniogJoL+MpPvvA="));
    }

    @ParameterizedTest
    @MethodSource("djangoHashes")
    void acceptsPasswordsCreatedByDjango(String password, String hash) {
        assertThat(encoder.matches(password, hash)).isTrue();
        assertThat(encoder.matches(password + " ", hash)).isFalse();
    }

    @Test
    void createsHashesInTheDjangoFormat() {
        String hash = encoder.encode("Prueba#2026");
        assertThat(hash).matches("bcrypt_sha256\\$\\$2b\\$12\\$[./0-9A-Za-z]{53}").hasSizeLessThanOrEqualTo(128);
        assertThat(encoder.matches("Prueba#2026", hash)).isTrue();
        assertThat(encoder.encode("Prueba#2026")).isNotEqualTo(hash);
    }

    @Test
    void onlyOldHashesAreUpgraded() {
        assertThat(encoder.upgradeEncoding(encoder.encode("Prueba#2026"))).isFalse();
        assertThat(encoder.upgradeEncoding("pbkdf2_sha256$1000$salt$hash")).isTrue();
        assertThat(encoder.upgradeEncoding("bcrypt_sha256$$2b$10$resto")).isTrue();
    }

    @Test
    void longPasswordsAreNotTruncated() {
        String hash = encoder.encode("A!" + "x".repeat(98));
        assertThat(encoder.matches("A!" + "x".repeat(97) + "y", hash)).isFalse();
    }

    @Test
    void rejectsOtherAlgorithms() {
        assertThat(encoder.matches("Prueba#2026", null)).isFalse();
        assertThat(encoder.matches("Prueba#2026", "!sin-clave-utilizable")).isFalse();
        assertThat(encoder.matches("Prueba#2026", "pbkdf2_sha256$mal")).isFalse();
        assertThat(encoder.matches("Prueba#2026", "pbkdf2_sha1$1000$salt$hash")).isFalse();
        // bcrypt puro, sin el SHA-256 ni el prefijo de Django.
        assertThat(encoder.matches("Prueba#2026",
                "$2b$12$xqw0H0kJNTqMpgUrd6Y6FOZopA3BLpbCQTZ53WN.GDMLmdx4IjWSG")).isFalse();
    }
}
