package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;

@Entity
@Table(name = "wallets")
public class Wallet {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, precision = 18, scale = 2)
    private BigDecimal balance;

    @Column(name = "bonus_balance", nullable = false, precision = 18, scale = 2)
    private BigDecimal bonusBalance;

    protected Wallet() {
    }

    public Wallet(Long userId, BigDecimal balance, BigDecimal bonusBalance) {
        this.userId = userId;
        this.balance = balance;
        this.bonusBalance = bonusBalance;
    }

    /** El bono se gasta primero y nunca queda negativo. */
    public void debit(BigDecimal amount) {
        balance = balance.subtract(amount);
        bonusBalance = bonusBalance.subtract(amount).max(BigDecimal.ZERO);
    }

    public Long getId() {
        return id;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getBonusBalance() {
        return bonusBalance;
    }
}
