package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Una sesión por JWT. Django la revoca al cerrar sesión, cambiar la contraseña, suspender o autoexcluir. */
@Entity
@Table(name = "accounts_authsession")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, updatable = false)
    private String jti;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at", updatable = false)
    private Instant revokedAt;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    protected AuthSession() {
    }

    public AuthSession(Long userId, String jti, Instant startedAt, Instant expiresAt) {
        this.userId = userId;
        this.jti = jti;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsableBy(Long user, Instant now) {
        return userId.equals(user) && revokedAt == null && expiresAt.isAfter(now);
    }

    public Long getId() {
        return id;
    }
}
