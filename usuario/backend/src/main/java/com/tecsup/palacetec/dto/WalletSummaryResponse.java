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

    private static final BigDecimal LOW_BALANCE_THRESHOLD = new BigDecimal("10.00");

    public static WalletSummaryResponse from(WalletBalance wallet) {
        return new WalletSummaryResponse(Formats.money(wallet.getBalance()), Formats.money(wallet.getBonusBalance()),
                Formats.money(wallet.getWinningsBalance()), Formats.money(LOW_BALANCE_THRESHOLD),
                wallet.getBalance().compareTo(LOW_BALANCE_THRESHOLD) < 0);
    }
}
