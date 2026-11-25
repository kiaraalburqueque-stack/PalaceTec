package com.tecsup.palacetec.service;

import com.tecsup.palacetec.config.TimeConfig;
import com.tecsup.palacetec.entity.User;
import com.tecsup.palacetec.entity.Wallet;
import com.tecsup.palacetec.entity.WalletTransaction;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.repository.WalletRepository;
import com.tecsup.palacetec.repository.WalletTransactionRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Usuario, billetera y bono de bienvenida: se guardan los tres o ninguno. */
@Service
public class RegistrationService {

    public static final BigDecimal WELCOME_BONUS = new BigDecimal("1000.00");

    private final UserRepository users;
    private final WalletRepository wallets;
    private final WalletTransactionRepository transactions;
    private final Clock clock;

    public RegistrationService(UserRepository users, WalletRepository wallets,
                               WalletTransactionRepository transactions, Clock clock) {
        this.users = users;
        this.wallets = wallets;
        this.transactions = transactions;
        this.clock = clock;
    }

    @Transactional
    public User create(String email, String dni, LocalDate birthDate, String passwordHash) {
        Instant now = TimeConfig.now(clock);
        User user = users.save(new User(email, dni, birthDate, passwordHash, now));
        Wallet wallet = wallets.save(new Wallet(user.getId(), WELCOME_BONUS, WELCOME_BONUS));
        transactions.save(new WalletTransaction(wallet.getId(), WalletTransaction.Type.WELCOME_BONUS, WELCOME_BONUS,
                wallet.getBalance(), null, now));
        return user;
    }
}
