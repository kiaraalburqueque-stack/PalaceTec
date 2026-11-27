package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.RiskIncident;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskIncidentRepository extends JpaRepository<RiskIncident, Long> {

    boolean existsByUserIdAndPlayId(Long userId, Long playId);

    Optional<RiskIncident> findByIdAndUserId(Long id, Long userId);

    List<RiskIncident> findTop20ByUserIdOrderByIdDesc(Long userId);
}
