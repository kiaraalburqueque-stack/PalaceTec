package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.FavoriteGame;
import java.util.Set;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface FavoriteGameRepository extends Repository<FavoriteGame, Long> {

    @Query("select f.gameId from FavoriteGame f where f.userId = :userId")
    Set<Long> findGameIds(Long userId);
}
