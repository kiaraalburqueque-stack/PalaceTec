package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.LoginHistory;
import jakarta.persistence.LockModeType;
import java.net.InetAddress;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndIpAddress(Long userId, InetAddress ipAddress);

    boolean existsByUserIdAndUserAgent(Long userId, String userAgent);

    /** El bloqueo hace que dos clics simultáneos en "No fui yo" se atiendan uno detrás del otro. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select h from LoginHistory h where h.reportTokenHash = :hash")
    Optional<LoginHistory> lockByReportTokenHash(String hash);
}
