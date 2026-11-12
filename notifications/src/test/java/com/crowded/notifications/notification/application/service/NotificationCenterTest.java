package com.crowded.notifications.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.notifications.notification.application.dto.NotificationItem;
import com.crowded.notifications.notification.application.dto.NotificationList;
import com.crowded.notifications.notification.domain.exception.NotificationNotFoundException;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationText;
import com.crowded.notifications.notification.domain.model.NotificationType;
import com.crowded.notifications.notification.domain.model.Recipient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationCenterTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID BRUNO = UUID.randomUUID();
  private static final UUID FADO = UUID.randomUUID();
  private static final UUID GONE = UUID.randomUUID();

  private final InMemoryNotifications notifications = new InMemoryNotifications();
  private int nameLookups;
  private final NotificationCenter center =
      new NotificationCenter(
          notifications,
          ids -> {
            nameLookups++;
            return Map.of(FADO, "Noite de Fado");
          },
          Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void listsOnlyTheUsersNotificationsNewestFirstWithTheEventNameAndUnreadCount() {
    Notification older = notification(ANA, FADO, NOW.minusSeconds(3600));
    Notification newer = notification(ANA, GONE, NOW.minusSeconds(60));
    notification(BRUNO, FADO, NOW);
    older.markRead(NOW.minusSeconds(30));

    NotificationList list = center.list(ANA);

    assertThat(list.notifications())
        .extracting(NotificationItem::id)
        .containsExactly(newer.getId(), older.getId());
    assertThat(list.notifications())
        .extracting(NotificationItem::eventName)
        .as("an event that no longer exists has no name")
        .containsExactly(null, "Noite de Fado");
    assertThat(list.notifications().get(1))
        .extracting(
            NotificationItem::type,
            NotificationItem::title,
            NotificationItem::body,
            NotificationItem::eventId,
            NotificationItem::causeId,
            NotificationItem::createdAt,
            NotificationItem::readAt)
        .containsExactly(
            NotificationType.OFFICIAL_CHANGE,
            "Adiado",
            "Nova data.\nMensagem.",
            FADO,
            older.getCauseId(),
            older.getCreatedAt(),
            NOW.minusSeconds(30));
    assertThat(list.unreadCount()).isOne();
  }

  @Test
  void listsNothingWithoutLookingUpEventsWhenTheUserHasNoNotifications() {
    NotificationList list = center.list(ANA);

    assertThat(list.notifications()).isEmpty();
    assertThat(list.unreadCount()).isZero();
    assertThat(nameLookups).isZero();
  }

  @Test
  void marksTheUsersNotificationReadOnceKeepingTheFirstTime() {
    Notification notification = notification(ANA, FADO, NOW.minusSeconds(60));

    center.markRead(ANA, notification.getId());
    center.markRead(ANA, notification.getId());

    assertThat(notification.getReadAt()).isEqualTo(NOW);
    assertThat(notifications.stampsSaved).isOne();
  }

  @Test
  void refusesAnotherUsersNotificationAsIfItDidNotExist() {
    Notification bruno = notification(BRUNO, FADO, NOW);
    UUID missing = UUID.randomUUID();

    assertThatThrownBy(() -> center.markRead(ANA, bruno.getId()))
        .isInstanceOf(NotificationNotFoundException.class)
        .hasFieldOrPropertyWithValue("code", "NOTIFICATION_NOT_FOUND");
    assertThatThrownBy(() -> center.markRead(ANA, missing))
        .isInstanceOf(NotificationNotFoundException.class);
    assertThat(bruno.isRead()).isFalse();
  }

  private Notification notification(UUID userId, UUID eventId, Instant createdAt) {
    Notification notification =
        Notification.create(
            UUID.randomUUID(),
            new Recipient(userId, true),
            eventId,
            NotificationType.OFFICIAL_CHANGE,
            UUID.randomUUID(),
            new NotificationText("Adiado", "Nova data.\nMensagem."),
            createdAt);
    notifications.addIfAbsent(notification);
    return notification;
  }
}
