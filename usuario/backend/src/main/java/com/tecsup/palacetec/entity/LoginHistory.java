package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.net.InetAddress;
import java.time.Instant;
import org.hibernate.annotations.DynamicUpdate;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Accesos exitosos, para detectar IP o dispositivo nuevos (HU56). */
@Entity
@DynamicUpdate
@Table(name = "login_history")
public class LoginHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "ip_address", updatable = false)
    private InetAddress ipAddress;

    @Column(name = "user_agent", nullable = false, length = 255, updatable = false)
    private String userAgent;

    @Column(name = "is_new_ip", nullable = false, updatable = false)
    private boolean newIp;

    @Column(name = "is_new_device", nullable = false, updatable = false)
    private boolean newDevice;

    @Column(name = "alert_sent", nullable = false)
    private boolean alertSent;

    /** Hash del token del botón "No fui yo", que atiende POST /auth/not-me. */
    @Column(name = "report_token_hash", length = 64)
    private String reportTokenHash;

    @Column(name = "reported_at")
    private Instant reportedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoginHistory() {
    }

    public LoginHistory(Long userId, InetAddress ipAddress, String userAgent, boolean newIp, boolean newDevice,
                        Instant createdAt) {
        this.userId = userId;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.newIp = newIp;
        this.newDevice = newDevice;
        this.createdAt = createdAt;
    }

    public void alertSent(String tokenHash) {
        this.alertSent = true;
        this.reportTokenHash = tokenHash;
    }

    public void report(Instant now) {
        reportedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getReportedAt() {
        return reportedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
