package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.DailyLimit;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailyLimitRepository extends JpaRepository<DailyLimit, Long> {

    Optional<DailyLimit> findByUserId(Long userId);
}
