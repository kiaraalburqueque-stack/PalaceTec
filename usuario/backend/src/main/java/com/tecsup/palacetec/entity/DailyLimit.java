package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;

@Entity
@Table(name = "responsible_dailylimit")
public class DailyLimit {

    public static final Duration INCREASE_DELAY = Duration.ofHours(24);

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "pending_amount", precision = 12, scale = 2)
    private BigDecimal pendingAmount;

    @Column(name = "effective_at")
    private Instant effectiveAt;

    protected DailyLimit() {
    }

    public DailyLimit(Long userId) {
        this.userId = userId;
    }

    /** Aplica el aumento pendiente cuando ya pasaron sus 24 horas. */
    public void activate(Instant now) {
        if (effectiveAt != null && !effectiveAt.isAfter(now)) {
            amount = pendingAmount;
            clearPending();
        }
    }

    /** Límite vigente, contando un aumento que ya pasó sus 24 horas aunque todavía no se haya guardado. */
    public BigDecimal amountAt(Instant now) {
        boolean increased = amount != null && pendingAmount != null && effectiveAt != null && !effectiveAt.isAfter(now);
        return increased ? pendingAmount : amount;
    }

    /** Primer límite o reducción: inmediatos. Aumento: en 24 horas; repetir el mismo importe no reinicia la espera. */
    public void request(BigDecimal value, Instant now) {
        if (amount == null || value.compareTo(amount) <= 0) {
            amount = value;
            clearPending();
        } else if (pendingAmount == null || pendingAmount.compareTo(value) != 0) {
            pendingAmount = value;
            effectiveAt = now.plus(INCREASE_DELAY);
        }
    }

    private void clearPending() {
        pendingAmount = null;
        effectiveAt = null;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public BigDecimal getPendingAmount() {
        return pendingAmount;
    }

    public Instant getEffectiveAt() {
        return effectiveAt;
    }
}
