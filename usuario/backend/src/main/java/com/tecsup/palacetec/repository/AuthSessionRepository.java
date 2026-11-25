package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.AuthSession;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    Optional<AuthSession> findByJti(String jti);
}
