package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.AssistantMessage;
import java.util.List;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AssistantMessageRepository extends JpaRepository<AssistantMessage, Long> {

    List<AssistantMessage> findByUserIdOrderByCreatedAtDescIdDesc(Long userId, Limit limit);
}
