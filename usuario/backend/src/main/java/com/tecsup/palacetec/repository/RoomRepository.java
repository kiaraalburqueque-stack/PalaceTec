package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.Room;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface RoomRepository extends Repository<Room, Long> {

    @Query("""
            select r from Room r left join fetch r.game g
            where r.active = true and (g.id is null or (g.published = true and g.deletedAt is null))
            order by r.id""")
    List<Room> findAvailable();

    @Query("select r from Room r left join fetch r.game where r.id = :id")
    Optional<Room> findById(Long id);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Room r where r.id = :id")
    Optional<Room> lockById(Long id);

    @Query("""
            select c.roomId as roomId, count(distinct c.userId) as players
            from RoomConnection c where c.expiresAt > :now group by c.roomId""")
    List<RoomPlayers> countPlayers(Instant now);

    interface RoomPlayers {
        Long getRoomId();

        Long getPlayers();
    }
}
