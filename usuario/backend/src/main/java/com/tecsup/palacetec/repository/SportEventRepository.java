package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.SportEvent;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

public interface SportEventRepository extends Repository<SportEvent, Long> {

    List<SportEvent> findTop12ByOrderByStartsAtDesc();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from SportEvent e where e.id = :id")
    Optional<SportEvent> lockById(Long id);
}
