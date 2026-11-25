package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.Bet;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
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

    /** De la más reciente a la más antigua. */
    @Query("""
            select b from Bet b join fetch b.round r join fetch r.room s left join fetch s.game
            where b.userId = :userId and b.createdAt >= :start and b.createdAt < :end
            order by b.createdAt desc, b.id desc""")
    List<Bet> findForExport(Long userId, Instant start, Instant end, Limit limit);

    @Query("select b from Bet b join fetch b.round where b.userId = :userId and b.round.id = :roundId order by b.id")
    List<Bet> findByRound(Long userId, Long roundId);

    @Query("select b from Bet b join fetch b.round where b.repeatOperationId = :operationId order by b.id")
    List<Bet> findByRepeatOperation(Long operationId);

    /**
     * Rondas y créditos por juego (null: salas sin juego), de un jugador o, sin él, de todos. Varias apuestas de un
     * jugador en la misma ronda cuentan como una ronda.
     */
    @Query(nativeQuery = true, value = """
            select g.game_id as gameId, count(*) as rounds, sum(g.total) as wagered from (
                select s.game_id, b.round_id, b.user_id, sum(b.amount) as total
                from user_bets b join game_rounds r on r.id = b.round_id join rooms s on s.id = r.room_id
                where b.created_at >= :start and b.created_at < :end
                  and (cast(:userId as bigint) is null or b.user_id = cast(:userId as bigint))
                group by s.game_id, b.round_id, b.user_id
            ) g group by g.game_id order by g.game_id""")
    List<RoomActivity> sumActivity(Long userId, Instant start, Instant end);

    interface RoomActivity {
        Long getGameId();

        long getRounds();

        BigDecimal getWagered();
    }

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
