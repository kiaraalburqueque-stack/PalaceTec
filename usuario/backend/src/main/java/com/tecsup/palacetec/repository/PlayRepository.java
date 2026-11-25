package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.Play;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface PlayRepository extends Repository<Play, Long> {

    List<Play> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    /** De la más reciente a la más antigua. */
    @Query("""
            select p from Play p where p.userId = :userId and p.createdAt >= :start and p.createdAt < :end
            order by p.createdAt desc, p.id desc""")
    List<Play> findForExport(Long userId, Instant start, Instant end, Limit limit);

    /** Cada jugada es una ronda; sin jugador, las de todos. */
    @Query(nativeQuery = true, value = """
            select kind as kind, count(*) as rounds, sum(amount) as wagered from casino_play
            where created_at >= :start and created_at < :end
              and (cast(:userId as bigint) is null or user_id = cast(:userId as bigint))
            group by kind order by kind""")
    List<KindActivity> sumActivity(Long userId, Instant start, Instant end);

    interface KindActivity {
        String getKind();

        long getRounds();

        BigDecimal getWagered();
    }

    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, Instant start, Instant end);
}
