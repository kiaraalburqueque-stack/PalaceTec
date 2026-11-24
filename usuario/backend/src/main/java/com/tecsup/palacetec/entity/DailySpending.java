package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Monto apostado por día calendario de Lima. */
@Entity
@Table(name = "responsible_dailyspending")
public class DailySpending {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false)
    private LocalDate date;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    protected DailySpending() {
    }

    public DailySpending(Long userId, LocalDate date) {
        this.userId = userId;
        this.date = date;
        this.amount = BigDecimal.ZERO;
    }

    public void add(BigDecimal value) {
        amount = amount.add(value);
    }

    public BigDecimal getAmount() {
        return amount;
    }
}
