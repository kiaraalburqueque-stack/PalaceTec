package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.Play;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

public interface PlayRepository extends Repository<Play, Long> {

    List<Play> findByUserIdAndIdIn(Long userId, Collection<Long> ids);

    long countByUserIdAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(Long userId, Instant start, Instant end);
}
