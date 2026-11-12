package com.crowded.notifications.notification.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationText;
import com.crowded.notifications.notification.domain.model.NotificationType;
import com.crowded.notifications.notification.domain.model.Recipient;
import com.crowded.notifications.notification.infrastructure.persistence.entity.NotificationEntity;
import com.crowded.notifications.notification.infrastructure.persistence.mapper.NotificationPersistenceMapperImpl;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class NotificationRepositoryAdapterTest {

  private final SpringDataNotificationRepository rows =
      mock(SpringDataNotificationRepository.class);
  private final NotificationRepositoryAdapter notifications =
      new NotificationRepositoryAdapter(rows, new NotificationPersistenceMapperImpl());

  private final Notification notification =
      Notification.create(
          UUID.randomUUID(),
          new Recipient(UUID.randomUUID(), true),
          UUID.randomUUID(),
          NotificationType.OFFICIAL_CHANGE,
          UUID.randomUUID(),
          new NotificationText("Cancelado", "A organização cancelou o evento."),
          Instant.parse("2026-11-12T10:00:00Z"));

  @Test
  void insertsEveryColumnOfTheNotification() {
    when(rows.insertIfAbsent(any())).thenReturn(1);

    boolean stored = notifications.addIfAbsent(notification);

    assertThat(stored).isTrue();
    ArgumentCaptor<NotificationEntity> row = ArgumentCaptor.forClass(NotificationEntity.class);
    verify(rows).insertIfAbsent(row.capture());
    assertThat(row.getValue())
        .extracting(
            NotificationEntity::getId,
            NotificationEntity::getUserId,
            NotificationEntity::getEventId,
            NotificationEntity::getType,
            NotificationEntity::getCauseId,
            NotificationEntity::getTitle,
            NotificationEntity::getBody,
            NotificationEntity::getDeduplicationKey,
            NotificationEntity::getCreatedAt)
        .containsExactly(
            notification.getId(),
            notification.getUserId(),
            notification.getEventId(),
            notification.getType(),
            notification.getCauseId(),
            notification.getTitle(),
            notification.getBody(),
            notification.getDeduplicationKey(),
            notification.getCreatedAt());
  }

  @Test
  void reportsANotificationThatAlreadyExists() {
    when(rows.insertIfAbsent(any())).thenReturn(0);

    assertThat(notifications.addIfAbsent(notification)).isFalse();
  }
}
