package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.BlackjackHand;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface BlackjackHandRepository extends JpaRepository<BlackjackHand, Long> {

    boolean existsByUserIdAndActiveTrue(Long userId);

    @Query("select h from BlackjackHand h join fetch h.play p where p.id = :playId")
    Optional<BlackjackHand> findByPlay(Long playId);

    @Query("select h from BlackjackHand h join fetch h.play where h.userId = :userId order by h.id desc")
    List<BlackjackHand> findLatest(Long userId, Limit limit);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from BlackjackHand h where h.id = :id and h.userId = :userId")
    Optional<BlackjackHand> lockByIdAndUserId(Long id, Long userId);
}
