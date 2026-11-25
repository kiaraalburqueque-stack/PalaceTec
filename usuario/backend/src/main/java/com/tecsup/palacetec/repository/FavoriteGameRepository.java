package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.FavoriteGame;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface FavoriteGameRepository extends Repository<FavoriteGame, Long> {

    @Query("select f.gameId from FavoriteGame f where f.userId = :userId")
    Set<Long> findGameIds(Long userId);

    /** Del más reciente al más antiguo. */
    @Query("select f.gameId from FavoriteGame f where f.userId = :userId order by f.createdAt desc, f.id desc")
    List<Long> findGameIdsNewestFirst(Long userId);

    @Modifying
    @Query("delete from FavoriteGame f where f.userId = :userId and f.gameId = :gameId")
    int remove(Long userId, Long gameId);

    /** Con dos clics simultáneos, el favorito ya quedó guardado. */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into favorite_games (user_id, game_id, created_at) values (:userId, :gameId, :now)
            on conflict do nothing""")
    void add(Long userId, Long gameId, Instant now);
}
