package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;

/** Una sesión por login. La revocan los dos backends al cerrarla, cambiar la contraseña, suspender o autoexcluir. */
@Entity
@DynamicUpdate
@Table(name = "accounts_authsession")
public class AuthSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64, updatable = false)
    private String jti;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Fin de la sesión web, que las renovaciones no extienden. La app móvil no renueva: queda en null. */
    @Column(name = "refresh_expires_at", updatable = false)
    private Instant refreshExpiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    protected AuthSession() {
    }

    public AuthSession(Long userId, String jti, Instant startedAt, Instant expiresAt, Instant refreshExpiresAt) {
        this.userId = userId;
        this.jti = jti;
        this.startedAt = startedAt;
        this.expiresAt = expiresAt;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    public boolean isUsableBy(Long user, Instant now) {
        return userId.equals(user) && revokedAt == null && expiresAt.isAfter(now);
    }

    public boolean isRenewableBy(Long user, Instant now) {
        return userId.equals(user) && revokedAt == null && refreshExpiresAt != null && refreshExpiresAt.isAfter(now);
    }

    public void renew(Instant newExpiresAt) {
        expiresAt = newExpiresAt;
    }

    public void revoke(Instant now) {
        revokedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getJti() {
        return jti;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getRefreshExpiresAt() {
        return refreshExpiresAt;
    }
}
