package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.WalletTransaction;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface WalletTransactionRepository extends JpaRepository<WalletTransaction, Long> {

    @Query("""
            select t.reference as reference, t.balanceAfter as balanceAfter from WalletTransaction t
            where t.walletId in (select w.id from Wallet w where w.userId = :userId) and t.reference in :references""")
    List<BalanceAfter> findBalances(Long userId, Collection<String> references);

    interface BalanceAfter {
        String getReference();

        BigDecimal getBalanceAfter();
    }

    /** Del más reciente al más antiguo. */
    @Query("""
            select t from WalletTransaction t
            where t.walletId in (select w.id from Wallet w where w.userId = :userId) and t.createdAt < :before
            order by t.createdAt desc, t.id desc""")
    List<WalletTransaction> findBefore(Long userId, Instant before, Limit limit);

    /** Del más antiguo al más reciente. */
    @Query("""
            select t from WalletTransaction t
            where t.walletId in (select w.id from Wallet w where w.userId = :userId) and t.createdAt >= :start
            order by t.createdAt, t.id""")
    List<WalletTransaction> findSince(Long userId, Instant start);
}
