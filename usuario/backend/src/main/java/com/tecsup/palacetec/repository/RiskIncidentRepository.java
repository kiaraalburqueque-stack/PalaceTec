package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.RiskIncident;
import java.util.List;
import java.util.Optional;
import org.springframework.data.repository.Repository;

public interface RiskIncidentRepository extends Repository<RiskIncident, Long> {

    Optional<RiskIncident> findByIdAndUserId(Long id, Long userId);

    List<RiskIncident> findTop20ByUserIdOrderByIdDesc(Long userId);
}
