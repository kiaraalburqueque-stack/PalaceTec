package com.tecsup.palacetec.service;

import com.tecsup.palacetec.dto.ProfileResponse;
import com.tecsup.palacetec.dto.WalletSummaryResponse;
import com.tecsup.palacetec.exception.ApiException;
import com.tecsup.palacetec.repository.UserRepository;
import com.tecsup.palacetec.repository.WalletBalanceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consultas del jugador autenticado sobre sus propios datos. */
@Service
@Transactional(readOnly = true)
public class ProfileService {

    private final UserRepository users;
    private final WalletBalanceRepository balances;

    public ProfileService(UserRepository users, WalletBalanceRepository balances) {
        this.users = users;
        this.balances = balances;
    }

    public ProfileResponse profile(Long userId) {
        return users.findById(userId).map(ProfileResponse::from)
                .orElseThrow(() -> ApiException.notFound("No encontrado."));
    }

    public WalletSummaryResponse walletSummary(Long userId) {
        return balances.findByUserId(userId).map(WalletSummaryResponse::from)
                .orElseThrow(() -> ApiException.notFound("Esta cuenta no tiene billetera."));
    }
}
