package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Spring crea la apuesta sin pago; payout lo escribe Django al liquidar la ronda. */
@Entity
@Table(name = "user_bets")
public class Bet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "session_id", updatable = false)
    private Long sessionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false, updatable = false)
    private GameRound round;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 11, updatable = false)
    private BetKind kind;

    @Column(nullable = false, length = 5, updatable = false)
    private String selection;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private List<Integer> chips;

    @Column(nullable = false, precision = 12, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(precision = 18, scale = 2, insertable = false, updatable = false)
    private BigDecimal payout;

    @Column(nullable = false, updatable = false)
    private UUID reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Bet() {
    }

    public Bet(Long userId, Long sessionId, GameRound round, BetKind kind, String selection, List<Integer> chips,
               BigDecimal amount, UUID reference, Instant createdAt) {
        this.userId = userId;
        this.sessionId = sessionId;
        this.round = round;
        this.kind = kind;
        this.selection = selection;
        this.chips = chips;
        this.amount = amount;
        this.reference = reference;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public GameRound getRound() {
        return round;
    }

    public BetKind getKind() {
        return kind;
    }

    public String getSelection() {
        return selection;
    }

    public List<Integer> getChips() {
        return chips;
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

    public Instant getCreatedAt() {
        return createdAt;
    }
}
