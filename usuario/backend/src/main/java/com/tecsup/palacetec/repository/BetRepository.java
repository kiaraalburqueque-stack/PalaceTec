package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.Bet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface BetRepository extends JpaRepository<Bet, Long> {

    @Query("select b from Bet b join fetch b.round where b.userId = :userId and b.reference = :reference")
    Optional<Bet> findByReference(Long userId, UUID reference);

    @Query("select coalesce(sum(b.amount), 0) from Bet b where b.userId = :userId and b.round.id = :roundId")
    BigDecimal sumByRound(Long userId, Long roundId);

    @Query("""
            select b from Bet b join fetch b.round r join fetch r.room s left join fetch s.game
            where b.userId = :userId and b.id in :ids""")
    List<Bet> findForHistory(Long userId, Collection<Long> ids);

    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, Instant start, Instant end);

    /** Ruleta y casino en una sola línea de tiempo, con el mismo orden que Django. */
    @Query(nativeQuery = true, value = """
            select h.source as source, h.id as id from (
                select 'roulette' as source, id, created_at from user_bets
                where user_id = :userId and created_at >= :start and created_at < :end
                union all
                select 'casino' as source, id, created_at from casino_play
                where user_id = :userId and created_at >= :start and created_at < :end
            ) h order by h.created_at desc, h.source desc, h.id desc limit :size offset :offset""")
    List<HistoryRow> findHistoryPage(Long userId, Instant start, Instant end, int size, long offset);

    interface HistoryRow {
        String getSource();

        Long getId();
    }
}
