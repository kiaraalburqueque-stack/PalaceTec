package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.RepeatOperation;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RepeatOperationRepository extends JpaRepository<RepeatOperation, Long> {

    Optional<RepeatOperation> findByUserIdAndReference(Long userId, UUID reference);
}
