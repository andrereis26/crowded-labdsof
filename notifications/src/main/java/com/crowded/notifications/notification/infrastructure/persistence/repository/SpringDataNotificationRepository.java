package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.infrastructure.persistence.entity.NotificationEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code notification}. */
interface SpringDataNotificationRepository extends Repository<NotificationEntity, UUID> {

  /**
   * Inserts {@code n} unless its deduplication key exists, also when another delivery of the same
   * message inserts it at the same time.
   *
   * @return 1 when inserted, 0 when it existed
   */
  @Modifying
  @Query(
      value =
          """
          INSERT INTO notification (
            id, user_id, event_id, type, cause_id, title, body, deduplication_key, created_at)
          VALUES (
            :#{#n.id}, :#{#n.userId}, :#{#n.eventId}, CAST(:#{#n.type.name()} AS notification_type),
            :#{#n.causeId}, :#{#n.title}, :#{#n.body}, :#{#n.deduplicationKey}, :#{#n.createdAt})
          ON CONFLICT (deduplication_key) DO NOTHING""",
      nativeQuery = true)
  int insertIfAbsent(@Param("n") NotificationEntity n);
}
