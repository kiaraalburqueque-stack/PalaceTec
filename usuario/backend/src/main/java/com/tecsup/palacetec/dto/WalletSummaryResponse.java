package com.tecsup.palacetec.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.tecsup.palacetec.entity.WalletBalance;

public record WalletSummaryResponse(
        String balance,
        @JsonProperty("bonus_balance") String bonusBalance,
        @JsonProperty("winnings_balance") String winningsBalance,
        @JsonProperty("low_balance_threshold") String lowBalanceThreshold,
        @JsonProperty("is_low_balance") boolean lowBalance) {

    public static WalletSummaryResponse from(WalletBalance wallet) {
        BalanceResponse balance = BalanceResponse.from(wallet.getBalance());
        return new WalletSummaryResponse(balance.balance(), Formats.money(wallet.getBonusBalance()),
                Formats.money(wallet.getWinningsBalance()), balance.lowBalanceThreshold(), balance.lowBalance());
    }
}
