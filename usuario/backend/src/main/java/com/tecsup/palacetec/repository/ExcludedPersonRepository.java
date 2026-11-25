package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.ExcludedPerson;
import org.springframework.data.repository.Repository;

public interface ExcludedPersonRepository extends Repository<ExcludedPerson, Long> {

    boolean existsByDniHash(String dniHash);
}
