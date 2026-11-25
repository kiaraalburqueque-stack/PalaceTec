package com.tecsup.palacetec.repository;

import com.tecsup.palacetec.entity.NotificationSetting;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface NotificationSettingRepository extends JpaRepository<NotificationSetting, Long> {

    Optional<NotificationSetting> findByUserId(Long userId);

    /** Con dos solicitudes a la vez, la segunda no falla ni duplica la fila. */
    @Modifying
    @Query(nativeQuery = true, value = """
            insert into notification_settings (user_id, promotions, security, game_alerts, updated_at)
            values (:userId, true, true, true, :now) on conflict (user_id) do nothing""")
    void createDefaults(Long userId, Instant now);
}
