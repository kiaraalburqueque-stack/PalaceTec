package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "wallet_transactions")
public class WalletTransaction {

    /** WIN solo lo escribe Django al liquidar. */
    public enum Type { WELCOME_BONUS, BET, WIN }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_id", nullable = false, updatable = false)
    private Long walletId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 13, updatable = false)
    private Type type;

    @Column(nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal amount;

    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2, updatable = false)
    private BigDecimal balanceAfter;

    @Column(updatable = false)
    private String reference;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WalletTransaction() {
    }

    public WalletTransaction(Long walletId, Type type, BigDecimal amount, BigDecimal balanceAfter, String reference,
                             Instant createdAt) {
        this.walletId = walletId;
        this.type = type;
        this.amount = amount;
        this.balanceAfter = balanceAfter;
        this.reference = reference;
        this.createdAt = createdAt;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
