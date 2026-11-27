package com.tecsup.palacetec.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.tecsup.palacetec.dto.Amounts;
import com.tecsup.palacetec.dto.BalanceResponse;
import com.tecsup.palacetec.dto.Formats;
import com.tecsup.palacetec.dto.PreBetCheckResponse;
import com.tecsup.palacetec.dto.WalletSummaryResponse;
import com.tecsup.palacetec.entity.Wallet;
import com.tecsup.palacetec.entity.WalletTransaction;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.WalletBalanceRepository;
import com.tecsup.palacetec.repository.WalletRepository;
import com.tecsup.palacetec.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Saldos, débitos de apuestas y premios de blackjack. Los de ruleta y deportes los acredita Django al liquidar. */
@Service
public class WalletService {

    private final WalletRepository wallets;
    private final WalletBalanceRepository balances;
    private final WalletTransactionRepository transactions;

    public WalletService(WalletRepository wallets, WalletBalanceRepository balances,
                         WalletTransactionRepository transactions) {
        this.wallets = wallets;
        this.balances = balances;
        this.transactions = transactions;
    }

    @Transactional(readOnly = true)
    public BalanceResponse balance(Long userId) {
        return BalanceResponse.from(find(userId).getBalance());
    }

    @Transactional(readOnly = true)
    public WalletSummaryResponse summary(Long userId) {
        return balances.findByUserId(userId).map(WalletSummaryResponse::from).orElseThrow(WalletService::missing);
    }

    /** Solo informa si el saldo alcanza: no reserva nada ni revisa los límites de la sala. */
    @Transactional(readOnly = true)
    public PreBetCheckResponse preBetCheck(Long userId, JsonNode amount) {
        BigDecimal value = Amounts.positive(amount).orElseThrow(() -> ApiException.badRequest(null, "Monto invalido."));
        BigDecimal balance = find(userId).getBalance();
        return new PreBetCheckResponse(Formats.money(balance), balance.compareTo(value) >= 0);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet lock(Long userId) {
        return wallets.lockByUserId(userId).orElseThrow(WalletService::missing);
    }

    private Wallet find(Long userId) {
        return wallets.findByUserId(userId).orElseThrow(WalletService::missing);
    }

    private static ApiException missing() {
        return ApiException.notFound("Esta cuenta no tiene billetera.");
    }

    /** La billetera ya está bloqueada. La referencia es única en la base, así que el cargo no puede repetirse. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void debit(Wallet wallet, BigDecimal amount, String reference, Instant now) {
        if (wallet.getBalance().compareTo(amount) < 0) {
            throw ApiException.badRequest("insufficient_balance", "Saldo insuficiente.");
        }
        wallet.debit(amount);
        transactions.save(new WalletTransaction(wallet.getId(), WalletTransaction.Type.BET, amount,
                wallet.getBalance(), reference, now));
    }

    /** Con la billetera ya bloqueada, como debit. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void credit(Wallet wallet, BigDecimal amount, String reference, Instant now) {
        wallet.credit(amount);
        transactions.save(new WalletTransaction(wallet.getId(), WalletTransaction.Type.WIN, amount,
                wallet.getBalance(), reference, now));
    }
}
