package com.tecsup.palacetec.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.InvalidWebTokenException;
import com.tecsup.palacetec.security.WebTokenService.Type;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;
import org.junit.jupiter.api.Test;

class WebTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-24T14:45:00Z");
    /** Token de SimpleJWT 5.5 con la misma clave ficticia; vence a las 15:00. */
    private static final String DJANGO = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0b2tlbl90eXBlIjoiYWNjZXNzIiwiZXhwIjox"
            + "Nzk1NTMyNDAwLCJpYXQiOjE3OTU1MzE1MDAsImp0aSI6IjAxMjM0NTY3ODlhYmNkZWYwMTIzNDU2Nzg5YWJjZGVmIiwidXNlcl9pZCI6Ij"
            + "ciLCJ2ZXJzaW9uIjozLCJzaWQiOiJmZWRjYmE5ODc2NTQzMjEwZmVkY2JhOTg3NjU0MzIxMCJ9.VhcgC6IrdQ9vihMUBdotjlxvgZWfhTl"
            + "bmVrYjtDijgU";

    private static WebTokenService service(Instant now) {
        var properties = new PalaceTecProperties(
                new PalaceTecProperties.Jwt("clave-ficticia-solo-para-pruebas-0123456789", Duration.ofHours(8)), "hmac",
                "http://localhost:8000", List.of("http://localhost:5173"), new PalaceTecProperties.Web(false, 30));
        return new WebTokenService(properties, Clock.fixed(now, ZoneOffset.UTC));
    }

    private static User user() {
        User user = mock(User.class);
        when(user.getId()).thenReturn(7L);
        when(user.getAuthVersion()).thenReturn(3);
        return user;
    }

    @Test
    void readsTheClaimsOfSimpleJwt() {
        var token = service(NOW).parse(DJANGO, Type.ACCESS);
        assertThat(token.userId()).isEqualTo(7);
        assertThat(token.version()).isEqualTo(3);
        assertThat(token.sid()).isEqualTo("fedcba9876543210fedcba9876543210");
        assertThat(token.expiresAt()).isEqualTo("2026-11-24T15:00:00Z");
    }

    @Test
    void writesTheClaimsOfSimpleJwt() {
        String[] parts = service(NOW).startSession(user()).value().split("\\.");
        assertThat(decode(parts[0])).contains("\"alg\":\"HS256\"");
        assertThat(decode(parts[1])).contains("\"token_type\":\"access\"", "\"user_id\":\"7\"", "\"version\":3",
                "\"iat\":1795531500", "\"exp\":1795532400", "\"jti\":\"", "\"sid\":\"");
    }

    @Test
    void refreshLastsEightHoursAndKeepsTheSession() {
        WebTokenService service = service(NOW);
        var access = service.startSession(user());
        var refresh = service.issueRefresh(user(), access.sid());
        assertThat(service.parse(refresh.value(), Type.REFRESH).sid()).isEqualTo(access.sid());
        assertThat(refresh.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(8)));
        assertThat(service.renew(user(), access.sid(), NOW.plusSeconds(90)).expiresAt()).isEqualTo(NOW.plusSeconds(90));
        assertThat(service.renew(user(), access.sid(), NOW.plusSeconds(9000)).expiresAt())
                .isEqualTo(NOW.plus(Duration.ofMinutes(15)));
    }

    @Test
    void rejectsExpiredForeignOrWrongTypeTokens() {
        var access = service(NOW).startSession(user());
        assertThat(service(NOW.plus(Duration.ofMinutes(15)).minusSeconds(1)).find(access.value(), Type.ACCESS))
                .isPresent();
        // Como SimpleJWT: el segundo exacto del vencimiento ya no vale.
        assertReason(service(NOW.plus(Duration.ofMinutes(15))), access.value(), Type.ACCESS, "Token is expired");
        assertReason(service(NOW), access.value(), Type.REFRESH, "Token has wrong type");
        assertReason(service(NOW), DJANGO + "x", Type.ACCESS, "Token is invalid");
        assertReason(service(NOW), "no-es-un-jwt", Type.ACCESS, "Token is invalid");
        assertThat(service(NOW).find(null, Type.ACCESS)).isEmpty();
    }

    private static void assertReason(WebTokenService service, String token, Type type, String reason) {
        assertThatThrownBy(() -> service.parse(token, type)).isInstanceOfSatisfying(InvalidWebTokenException.class,
                error -> assertThat(error.body().get("messages").toString()).contains(reason));
    }

    private static String decode(String part) {
        return new String(Base64.getUrlDecoder().decode(part), StandardCharsets.UTF_8);
    }
}
