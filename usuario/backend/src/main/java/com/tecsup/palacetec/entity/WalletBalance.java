package com.tecsup.palacetec.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import org.hibernate.annotations.Immutable;

/** Vista wallet_balances (solo lectura). */
@Entity
@Immutable
@Table(name = "wallet_balances")
public class WalletBalance {

    @Id
    private Long id;

    @Column(name = "user_id")
    private Long userId;

    private BigDecimal balance;

    @Column(name = "bonus_balance")
    private BigDecimal bonusBalance;

    @Column(name = "winnings_balance")
    private BigDecimal winningsBalance;

    protected WalletBalance() {
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public BigDecimal getBonusBalance() {
        return bonusBalance;
    }

    public BigDecimal getWinningsBalance() {
        return winningsBalance;
    }
}
