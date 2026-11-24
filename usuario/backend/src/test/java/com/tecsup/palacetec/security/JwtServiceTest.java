package com.tecsup.palacetec.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.entity.User;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private static final String SECRET = "clave-ficticia-solo-para-pruebas-0123456789";
    private static final Instant NOW = Instant.parse("2026-11-24T15:00:00Z");

    private static JwtService service(String secret, Instant now) {
        var properties = new PalaceTecProperties(new PalaceTecProperties.Jwt(secret, Duration.ofHours(8)), "hmac",
                "http://localhost:8000", List.of("http://localhost:5173"));
        return new JwtService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static User user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getAuthVersion()).thenReturn(3);
        return user;
    }

    @Test
    void identifiesPlayerSessionAndVersionForEightHours() {
        var token = service(SECRET, NOW).issue(user());
        assertThat(token.expiresIn()).isEqualTo(28800);
        assertThat(service(SECRET, NOW).parse(token.value())).isEqualTo(new PlayerPrincipal(7L, token.jti(), 3));
        assertThat(service(SECRET, NOW.plus(Duration.ofHours(8)).minusSeconds(1)).parse(token.value())).isNotNull();
        assertThatThrownBy(() -> service(SECRET, NOW.plus(Duration.ofHours(8)).plusSeconds(1)).parse(token.value()))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsForeignTokens() {
        JwtService service = service(SECRET, NOW);
        String otherKey = service("otra-clave-ficticia-de-pruebas-987654321000", NOW).issue(user()).value();
        assertThatThrownBy(() -> service.parse(otherKey)).isInstanceOf(JwtException.class);
        // Sin firma (alg none).
        String unsigned = Jwts.builder().issuer("palacetec-usuario").subject("7").id("x").claim("version", 3).compact();
        assertThatThrownBy(() -> service.parse(unsigned)).isInstanceOf(JwtException.class);
        // Misma clave, pero con las claims de un JWT de Django.
        String django = Jwts.builder().id("x").claim("user_id", "7").claim("token_type", "access").claim("version", 3)
                .signWith(Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8))).compact();
        assertThatThrownBy(() -> service.parse(django)).isInstanceOf(JwtException.class);
        assertThatThrownBy(() -> service.parse("no-es-un-jwt")).isInstanceOf(JwtException.class);
    }
}
