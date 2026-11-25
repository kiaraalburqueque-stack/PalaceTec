package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.Game;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface GameRepository extends Repository<Game, Long> {

    /** Publicados y con al menos una sala activa, o enlazados a su propia pantalla. */
    @Query("""
            select g from Game g
            where g.published = true and g.deletedAt is null
              and (g.playKind <> '' or exists (select r.id from Room r where r.game = g and r.active = true))
            order by g.id""")
    List<Game> findAvailable();

    @Query("select g from Game g where g.id = :id and g.published = true and g.deletedAt is null")
    Optional<Game> findPublished(Long id);

    /** También los retirados: su actividad pasada se sigue contando. */
    List<Game> findByPlayKindNotOrderById(String playKind);

    Optional<Game> findBySlug(String slug);

    @Query("""
            select r.game.id as gameId, min(r.minBet) as minBet, max(r.maxBet) as maxBet
            from Room r where r.active = true and r.game is not null group by r.game.id""")
    List<RoomLimits> findRoomLimits();

    interface RoomLimits {
        Long getGameId();

        BigDecimal getMinBet();

        BigDecimal getMaxBet();
    }
}
