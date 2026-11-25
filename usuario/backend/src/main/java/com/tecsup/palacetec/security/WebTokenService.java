package com.tecsup.palacetec.security;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.exception.InvalidWebTokenException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

/**
 * JWT de la web, con las mismas claims y el mismo algoritmo que SimpleJWT en Django. Viajan solo en cookies HttpOnly:
 * no sirven como Bearer ni el Bearer de la app móvil sirve como cookie.
 */
@Service
public class WebTokenService {

    /** SIMPLE_JWT["ACCESS_TOKEN_LIFETIME"] de Django. */
    public static final Duration ACCESS_LIFETIME = Duration.ofMinutes(15);
    /** AUTH_SESSION_LIFETIME de Django: renovar no extiende la sesión más allá de este plazo. */
    public static final Duration SESSION_LIFETIME = Duration.ofHours(8);

    private static final String TOKEN_TYPE = "token_type";
    private static final String USER_ID = "user_id";
    private static final String SID = "sid";
    private static final String VERSION = "version";

    private final SecretKey key;
    private final Clock clock;

    public WebTokenService(PalaceTecProperties properties, Clock clock) {
        this.key = new SecretKeySpec(properties.jwt().secret().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        this.clock = clock;
    }

    public enum Type { ACCESS, REFRESH }

    /** sid identifica la fila de accounts_authsession y se mantiene al renovar; jti cambia en cada token. */
    public record WebToken(String value, Long userId, String sid, int version, Instant expiresAt) {
    }

    /** Primer token de una sesión: su jti es también el sid. */
    public WebToken startSession(User user) {
        String jti = newId();
        return issue(user, Type.ACCESS, jti, jti, now().plus(ACCESS_LIFETIME));
    }

    public WebToken issueRefresh(User user, String sid) {
        return issue(user, Type.REFRESH, newId(), sid, now().plus(SESSION_LIFETIME));
    }

    /** Nuevo acceso de una sesión abierta, que nunca vence después que ella. */
    public WebToken renew(User user, String sid, Instant sessionEnd) {
        Instant expiresAt = now().plus(ACCESS_LIFETIME);
        Instant limit = sessionEnd.truncatedTo(ChronoUnit.SECONDS);
        return issue(user, Type.ACCESS, newId(), sid, limit.isBefore(expiresAt) ? limit : expiresAt);
    }

    /** Verifica firma, vencimiento y tipo; la revocación se comprueba contra la base. */
    public WebToken parse(String token, Type type) {
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).clock(() -> Date.from(clock.instant())).build()
                    .parseSignedClaims(token).getPayload();
        } catch (ExpiredJwtException exception) {
            throw new InvalidWebTokenException("Token is expired");
        } catch (JwtException | IllegalArgumentException exception) {
            throw new InvalidWebTokenException("Token is invalid");
        }
        if (claims.getExpiration() == null || !claims.getExpiration().toInstant().isAfter(clock.instant())) {
            throw new InvalidWebTokenException("Token is expired");
        }
        if (claims.getId() == null) {
            throw new InvalidWebTokenException("Token has no id");
        }
        if (!claimName(type).equals(claims.get(TOKEN_TYPE))) {
            throw new InvalidWebTokenException(
                    claims.containsKey(TOKEN_TYPE) ? "Token has wrong type" : "Token has no type");
        }
        return read(token, claims);
    }

    /** Igual que parse, sin distinguir por qué el token no sirve. */
    public Optional<WebToken> find(String token, Type type) {
        try {
            return token == null ? Optional.empty() : Optional.of(parse(token, type));
        } catch (InvalidWebTokenException exception) {
            return Optional.empty();
        }
    }

    private static WebToken read(String token, Claims claims) {
        try {
            Integer version = claims.get(VERSION, Integer.class);
            String sid = claims.get(SID, String.class);
            return new WebToken(token, Long.valueOf(claims.get(USER_ID, String.class)),
                    sid == null ? claims.getId() : sid, version == null ? 0 : version,
                    claims.getExpiration().toInstant());
        } catch (JwtException | NumberFormatException exception) {
            throw new InvalidWebTokenException("Token is invalid");
        }
    }

    private WebToken issue(User user, Type type, String jti, String sid, Instant expiresAt) {
        String value = Jwts.builder().claim(TOKEN_TYPE, claimName(type)).expiration(Date.from(expiresAt))
                .issuedAt(Date.from(now())).id(jti).claim(USER_ID, user.getId().toString())
                .claim(VERSION, user.getAuthVersion()).claim(SID, sid)
                .signWith(key, Jwts.SIG.HS256).compact();
        return new WebToken(value, user.getId(), sid, user.getAuthVersion(), expiresAt);
    }

    private Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.SECONDS);
    }

    private static String newId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private static String claimName(Type type) {
        return type == Type.ACCESS ? "access" : "refresh";
    }
}
