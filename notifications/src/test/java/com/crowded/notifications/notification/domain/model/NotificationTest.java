package com.crowded.notifications.notification.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationTest {

  @Test
  void theSameMessageGivesTheSameRecipientTheSameKey() {
    UUID messageId = UUID.randomUUID();
    Recipient recipient = new Recipient(UUID.randomUUID(), true);
    NotificationText text = new NotificationText("Cancelado", "A organização cancelou o evento.");
    UUID eventId = UUID.randomUUID();
    UUID causeId = UUID.randomUUID();
    Instant now = Instant.parse("2026-11-12T10:00:00Z");

    Notification first =
        Notification.create(
            messageId, recipient, eventId, NotificationType.OFFICIAL_CHANGE, causeId, text, now);
    Notification again =
        Notification.create(
            messageId, recipient, eventId, NotificationType.OFFICIAL_CHANGE, causeId, text, now);

    assertThat(again.getDeduplicationKey()).isEqualTo(first.getDeduplicationKey());
    assertThat(again.getId()).isNotEqualTo(first.getId());
    assertThat(first)
        .extracting(
            Notification::getUserId,
            Notification::getEventId,
            Notification::getCauseId,
            Notification::getTitle,
            Notification::getBody,
            Notification::getCreatedAt)
        .containsExactly(recipient.userId(), eventId, causeId, text.title(), text.body(), now);
    assertThat(Notification.deduplicationKey(recipient.userId(), UUID.randomUUID()))
        .isNotEqualTo(first.getDeduplicationKey());
    assertThat(first).isNotEqualTo(again).isEqualTo(first);
  }

  @Test
  void keepsTheFirstTimeItWasDeliveredAndRead() {
    Notification notification = notification(UUID.randomUUID());
    Instant first = Instant.parse("2026-11-12T10:01:00Z");
    Instant later = Instant.parse("2026-11-12T10:05:00Z");

    assertThat(notification.isRead()).isFalse();
    notification.markDelivered(first);
    notification.markDelivered(later);
    notification.markRead(first);
    notification.markRead(later);

    assertThat(notification.getDeliveredAt()).isEqualTo(first);
    assertThat(notification.getReadAt()).isEqualTo(first);
    assertThat(notification.isRead()).isTrue();
  }

  @Test
  void isForItsRecipientOnly() {
    UUID recipient = UUID.randomUUID();
    Notification notification = notification(recipient);

    assertThat(notification.isFor(recipient)).isTrue();
    assertThat(notification.isFor(UUID.randomUUID())).isFalse();
  }

  @Test
  void isRestoredAsStored() {
    Notification created = notification(UUID.randomUUID());
    Instant delivered = Instant.parse("2026-11-12T10:01:00Z");

    Notification restored =
        Notification.restore(
            created.getId(),
            created.getUserId(),
            created.getEventId(),
            created.getType(),
            created.getCauseId(),
            new NotificationText(created.getTitle(), created.getBody()),
            created.getDeduplicationKey(),
            created.getCreatedAt(),
            delivered,
            null);

    assertThat(restored).isEqualTo(created);
    assertThat(
            Notification.restore(
                    UUID.randomUUID(),
                    created.getUserId(),
                    created.getEventId(),
                    NotificationType.RECOGNITION,
                    null,
                    new NotificationText("O teu reporte fez a diferença", "Obrigado."),
                    "recognition",
                    created.getCreatedAt(),
                    null,
                    null)
                .getCauseId())
        .as("a recognition has no cause")
        .isNull();
    assertThat(restored.getDeliveredAt()).isEqualTo(delivered);
    assertThat(restored.isRead()).isFalse();
  }

  private static Notification notification(UUID recipient) {
    return Notification.create(
        UUID.randomUUID(),
        new Recipient(recipient, true),
        UUID.randomUUID(),
        NotificationType.OFFICIAL_CHANGE,
        UUID.randomUUID(),
        new NotificationText("Cancelado", "A organização cancelou o evento."),
        Instant.parse("2026-11-12T10:00:00Z"));
  }
}
