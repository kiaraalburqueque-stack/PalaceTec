package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.LoginHistory;
import java.net.InetAddress;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LoginHistoryRepository extends JpaRepository<LoginHistory, Long> {

    boolean existsByUserId(Long userId);

    boolean existsByUserIdAndIpAddress(Long userId, InetAddress ipAddress);

    boolean existsByUserIdAndUserAgent(Long userId, String userAgent);
}
