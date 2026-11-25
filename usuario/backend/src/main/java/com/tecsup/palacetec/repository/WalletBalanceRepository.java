package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.WalletBalance;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface WalletBalanceRepository extends Repository<WalletBalance, Long> {

    Optional<WalletBalance> findByUserId(Long userId);
}
