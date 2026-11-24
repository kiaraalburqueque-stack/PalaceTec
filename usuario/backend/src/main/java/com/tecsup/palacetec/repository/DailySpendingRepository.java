package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.DailySpending;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DailySpendingRepository extends JpaRepository<DailySpending, Long> {

    Optional<DailySpending> findByUserIdAndDate(Long userId, LocalDate date);
}
