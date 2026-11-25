package com.tecsup.palacetec.security;

import com.tecsup.palacetec.config.PalaceTecProperties;
import com.tecsup.palacetec.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

    private static final String ISSUER = "palacetec-usuario";
    private static final String VERSION = "version";

    private final SecretKey key;
    private final Duration expiration;
    private final Clock clock;

    public JwtService(PalaceTecProperties properties, Clock clock) {
        this.key = Keys.hmacShaKeyFor(properties.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.jwt().expiration();
        this.clock = clock;
    }

    public record IssuedToken(String value, String jti, Instant issuedAt, Instant expiresAt) {

        public long expiresIn() {
            return Duration.between(issuedAt, expiresAt).toSeconds();
        }
    }

    public IssuedToken issue(User user) {
        Instant issuedAt = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Instant expiresAt = issuedAt.plus(expiration);
        String jti = UUID.randomUUID().toString().replace("-", "");
        String token = Jwts.builder().issuer(ISSUER).subject(user.getId().toString()).id(jti)
                .claim(VERSION, user.getAuthVersion())
                .issuedAt(Date.from(issuedAt)).expiration(Date.from(expiresAt))
                .signWith(key).compact();
        return new IssuedToken(token, jti, issuedAt, expiresAt);
    }

    /** Verifica firma, emisor y vencimiento; la revocación se comprueba contra la base. */
    public PlayerPrincipal parse(String token) {
        Claims claims = Jwts.parser().verifyWith(key).requireIssuer(ISSUER)
                .clock(() -> Date.from(clock.instant())).build()
                .parseSignedClaims(token).getPayload();
        Integer version = claims.get(VERSION, Integer.class);
        if (claims.getId() == null || version == null) {
            throw new JwtException("Faltan datos en el token");
        }
        try {
            return new PlayerPrincipal(Long.valueOf(claims.getSubject()), claims.getId(), version);
        } catch (NumberFormatException exception) {
            throw new JwtException("Token sin usuario válido", exception);
        }
    }
}
