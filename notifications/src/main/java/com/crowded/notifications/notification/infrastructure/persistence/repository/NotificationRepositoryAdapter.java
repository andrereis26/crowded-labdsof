package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.repository.NotificationRepository;
import com.crowded.notifications.notification.infrastructure.persistence.mapper.NotificationPersistenceMapper;
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
}
