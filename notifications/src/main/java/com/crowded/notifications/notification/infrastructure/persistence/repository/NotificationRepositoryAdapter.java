package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.repository.NotificationRepository;
import com.crowded.notifications.notification.infrastructure.persistence.mapper.NotificationPersistenceMapper;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link NotificationRepository} on PostgreSQL. */
@Repository
class NotificationRepositoryAdapter implements NotificationRepository {

  private final SpringDataNotificationRepository notifications;
  private final NotificationPersistenceMapper mapper;

  NotificationRepositoryAdapter(
      SpringDataNotificationRepository notifications, NotificationPersistenceMapper mapper) {
    this.notifications = notifications;
    this.mapper = mapper;
  }

  @Override
  @Transactional
  public boolean addIfAbsent(Notification notification) {
    return notifications.insertIfAbsent(mapper.toEntity(notification)) == 1;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<Notification> findById(UUID id) {
    return notifications.findById(id).map(mapper::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<Notification> findByRecipient(UUID userId) {
    return notifications.findByUserIdOrderByCreatedAtDescIdDesc(userId).stream()
        .map(mapper::toDomain)
        .toList();
  }

  @Override
  @Transactional(readOnly = true)
  public long countUnread(UUID userId) {
    return notifications.countByUserIdAndReadAtIsNull(userId);
  }

  @Override
  @Transactional
  public void saveStamps(Notification notification) {
    notifications.stamp(
        notification.getId(), notification.getDeliveredAt(), notification.getReadAt());
  }
}
