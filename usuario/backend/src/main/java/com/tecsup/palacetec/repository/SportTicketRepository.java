package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.SportTicket;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SportTicketRepository extends JpaRepository<SportTicket, Long> {

    @Query("select t from SportTicket t join fetch t.play p where p.id = :playId")
    Optional<SportTicket> findByPlay(Long playId);

    @Query("select t from SportTicket t join fetch t.play p where p.userId = :userId order by t.id desc")
    List<SportTicket> findLatest(Long userId, Limit limit);
}
