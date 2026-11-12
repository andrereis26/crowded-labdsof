package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.infrastructure.persistence.entity.NotificationEntity;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
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

  Optional<NotificationEntity> findById(UUID id);

  /** Newest first; the id breaks ties, so the order is stable. */
  List<NotificationEntity> findByUserIdOrderByCreatedAtDescIdDesc(UUID userId);

  long countByUserIdAndReadAtIsNull(UUID userId);

  /** Stores the delivery and reading times that are not stored yet; a stored one is kept. */
  @Modifying
  @Query(
      value =
          """
          UPDATE notification
          SET delivered_at = COALESCE(delivered_at, :deliveredAt),
              read_at = COALESCE(read_at, :readAt)
          WHERE id = :id""",
      nativeQuery = true)
  int stamp(
      @Param("id") UUID id,
      @Param("deliveredAt") Instant deliveredAt,
      @Param("readAt") Instant readAt);
}
