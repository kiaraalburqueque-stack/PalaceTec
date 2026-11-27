package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.WalletBalance;
import java.math.BigDecimal;

public record WalletSummaryResponse(
        String balance,
        @JsonProperty("bonus_balance") String bonusBalance,
        @JsonProperty("winnings_balance") String winningsBalance,
        @JsonProperty("low_balance_threshold") String lowBalanceThreshold,
        @JsonProperty("is_low_balance") boolean lowBalance) {

    public static WalletSummaryResponse from(WalletBalance wallet) {
        return of(wallet.getBalance(), wallet.getBonusBalance(), wallet.getWinningsBalance());
    }

    /** También es el evento wallet:update de Django. Las ganancias son el saldo menos el bono. */
    public static WalletSummaryResponse of(BigDecimal total, BigDecimal bonus, BigDecimal winnings) {
        BalanceResponse balance = BalanceResponse.from(total);
        return new WalletSummaryResponse(balance.balance(), Formats.money(bonus), Formats.money(winnings),
                balance.lowBalanceThreshold(), balance.lowBalance());
    }
}
