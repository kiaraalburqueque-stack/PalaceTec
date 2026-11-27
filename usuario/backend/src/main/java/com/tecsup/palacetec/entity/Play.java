package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.DynamicUpdate;

/** Jugada de blackjack o de deportes. Su referencia es única por jugador: un reintento no cobra dos veces. */
@Entity
@DynamicUpdate
@Table(name = "casino_play")
public class Play {

    public static final String BLACKJACK = "BLACKJACK";
    public static final String SPORTS = "SPORTS";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "session_id", updatable = false)
    private Long sessionId;

    @Column(nullable = false, length = 12, updatable = false)
    private String kind;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(precision = 18, scale = 2)
    private BigDecimal payout;

    @Column(nullable = false, updatable = false)
    private UUID reference;

    @Column(nullable = false, length = 200, updatable = false)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected Play() {
    }

    public Play(Long userId, Long sessionId, String kind, BigDecimal amount, UUID reference, String description,
                Instant createdAt) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.kind = kind;
        this.amount = amount;
        this.reference = reference;
        this.description = description;
        this.createdAt = createdAt;
    }

    public void settle(BigDecimal total, Instant now) {
        payout = total;
        settledAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public String getKind() {
        return kind;
    }

    public String getKindLabel() {
        return kind.equals(BLACKJACK) ? "Blackjack" : "Deportes simulados";
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPayout() {
        return payout;
    }

    public UUID getReference() {
        return reference;
    }

    public String getDescription() {
        return description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getSettledAt() {
        return settledAt;
    }
}
