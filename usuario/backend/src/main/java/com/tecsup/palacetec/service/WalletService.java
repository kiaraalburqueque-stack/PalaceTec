package com.tecsup.palacetec.service;

import com.tecsup.palacetec.entity.Wallet;
import com.tecsup.palacetec.entity.WalletTransaction;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.WalletRepository;
import com.tecsup.palacetec.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Débitos de apuestas. Los premios los acredita Django al liquidar la ronda. */
@Service
public class WalletService {

    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;

    public WalletService(WalletRepository wallets, WalletTransactionRepository transactions) {
        this.wallets = wallets;
        this.transactions = transactions;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Wallet lock(Long userId) {
        return wallets.lockByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Esta cuenta no tiene billetera."));
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
}
