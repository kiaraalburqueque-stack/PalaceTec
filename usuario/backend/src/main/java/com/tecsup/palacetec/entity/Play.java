package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Jugadas de blackjack y deportes: el historial las une a las apuestas de ruleta. */
@Entity
@Immutable
@Table(name = "casino_play")
public class Play {

    @Id
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "session_id")
    private Long sessionId;

    @Column(nullable = false, length = 12)
    private String kind;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(precision = 18, scale = 2)
    private BigDecimal payout;

    @Column(nullable = false)
    private UUID reference;

    @Column(nullable = false, length = 200)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected Play() {
    }

    public Long getId() {
        return id;
    }

    public String getKind() {
        return kind;
    }

    public String getKindLabel() {
        return kind.equals("BLACKJACK") ? "Blackjack" : "Deportes simulados";
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
}
