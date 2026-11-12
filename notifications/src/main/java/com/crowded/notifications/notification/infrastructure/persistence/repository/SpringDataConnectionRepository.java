package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.infrastructure.persistence.entity.EventConnectionEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to Core's {@code event_connection}, with the notification settings. */
interface SpringDataConnectionRepository extends Repository<EventConnectionEntity, UUID> {

  @Query(
      value =
          """
          SELECT c.user_id AS "userId", t.id IS NOT NULL AS "ticket",
                 e.enabled AS "eventSetting", u.enabled AS "overallSetting"
          FROM event_connection c
          LEFT JOIN ticket t ON t.id = c.id
          LEFT JOIN access_code_connection a ON a.id = c.id
          LEFT JOIN event_notification_setting e
            ON e.event_connection_id = c.id AND e.type = CAST(:type AS notification_type)
          LEFT JOIN user_notification_setting u
            ON u.user_id = c.user_id AND u.type = CAST(:type AS notification_type)
          WHERE c.event_id = :eventId AND (t.id IS NOT NULL OR a.id IS NOT NULL)
          ORDER BY c.connected_at, c.id""",
      nativeQuery = true)
  List<ConnectionRow> findByEvent(@Param("eventId") UUID eventId, @Param("type") String type);
}
