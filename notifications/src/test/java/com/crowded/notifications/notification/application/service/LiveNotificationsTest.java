package com.crowded.notifications.notification.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.notifications.notification.application.dto.LiveNotification;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationText;
import com.crowded.notifications.notification.domain.model.NotificationType;
import com.crowded.notifications.notification.domain.model.Recipient;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LiveNotificationsTest {

  private static final Instant NOW = Instant.parse("2026-11-12T10:00:00Z");
  private static final UUID ANA = UUID.randomUUID();
  private static final UUID BRUNO = UUID.randomUUID();
  private static final UUID FADO = UUID.randomUUID();

  private final InMemoryNotifications notifications = new InMemoryNotifications();
  private final LiveNotifications live =
      new LiveNotifications(
          notifications, ids -> Map.of(FADO, "Noite de Fado"), Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void sendsANewNotificationToEveryStreamOfItsRecipientOnlyAndStampsItDelivered() {
    List<LiveNotification> phone = new ArrayList<>();
    List<LiveNotification> browser = new ArrayList<>();
    List<LiveNotification> bruno = new ArrayList<>();
    live.subscribe(ANA, phone::add);
    live.subscribe(ANA, browser::add);
    live.subscribe(BRUNO, bruno::add);
    notification(ANA);
    Notification latest = notification(ANA);

    live.deliver(latest.getId());

    assertThat(phone).singleElement().isEqualTo(browser.getFirst());
    assertThat(phone.getFirst().notification().id()).isEqualTo(latest.getId());
    assertThat(phone.getFirst().notification().eventName()).isEqualTo("Noite de Fado");
    assertThat(phone.getFirst().unreadCount()).isEqualTo(2);
    assertThat(bruno).isEmpty();
    assertThat(latest.getDeliveredAt()).isEqualTo(NOW);
    assertThat(notifications.stampsSaved).isOne();
  }

  @Test
  void stampsNothingWhenTheRecipientHasNoStreamOrTheNotificationIsGone() {
    Notification notification = notification(ANA);
    Runnable close = live.subscribe(ANA, sent -> {});
    close.run();
    close.run();

    live.deliver(notification.getId());
    live.deliver(UUID.randomUUID());

    assertThat(notification.getDeliveredAt()).isNull();
    assertThat(notifications.stampsSaved).isZero();
  }

  @Test
  void dropsAStreamThatFailsAndKeepsSendingToTheOthers() {
    List<LiveNotification> open = new ArrayList<>();
    int[] failures = {0};
    live.subscribe(
        ANA,
        sent -> {
          failures[0]++;
          throw new IllegalStateException("closed");
        });
    live.subscribe(ANA, open::add);

    live.deliver(notification(ANA).getId());
    live.deliver(notification(ANA).getId());

    assertThat(failures[0]).as("removed after its first failure").isOne();
    assertThat(open).hasSize(2);
  }

  @Test
  void stampsNothingWhenEveryStreamFails() {
    live.subscribe(
        ANA,
        sent -> {
          throw new IllegalStateException("closed");
        });
    Notification notification = notification(ANA);

    live.deliver(notification.getId());

    assertThat(notification.getDeliveredAt()).isNull();
  }

  private Notification notification(UUID userId) {
    Notification notification =
        Notification.create(
            UUID.randomUUID(),
            new Recipient(userId, true),
            FADO,
            NotificationType.OFFICIAL_CHANGE,
            UUID.randomUUID(),
            new NotificationText("Adiado", "Nova data."),
            NOW.minusSeconds(5));
    notifications.addIfAbsent(notification);
    return notification;
  }
}
