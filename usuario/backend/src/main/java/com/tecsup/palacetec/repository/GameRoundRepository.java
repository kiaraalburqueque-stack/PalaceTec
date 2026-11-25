package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.GameRound;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface GameRoundRepository extends Repository<GameRound, Long> {

    Optional<GameRound> findFirstByRoomIdOrderByIdDesc(Long roomId);

    List<GameRound> findTop10ByRoomIdAndStatusOrderBySettledAtDescIdDesc(Long roomId, GameRound.Status status);

    /** Última ronda liquidada de la sala en la que apostó el jugador. */
    @Query("""
            select max(r.id) from GameRound r
            where r.room.id = :roomId and r.status = com.tecsup.palacetec.entity.GameRound.Status.SETTLED
              and exists (select b.id from Bet b where b.round = r and b.userId = :userId)""")
    Optional<Long> findPreviousId(Long userId, Long roomId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from GameRound r where r.id = :id and r.room.id = :roomId")
    Optional<GameRound> lockByIdAndRoomId(Long id, Long roomId);
}
