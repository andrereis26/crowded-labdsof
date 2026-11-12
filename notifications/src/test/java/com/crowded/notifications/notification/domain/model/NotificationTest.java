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

    assertThat(again.deduplicationKey()).isEqualTo(first.deduplicationKey());
    assertThat(again.id()).isNotEqualTo(first.id());
    assertThat(first)
        .extracting(
            Notification::userId,
            Notification::eventId,
            Notification::causeId,
            Notification::title,
            Notification::body,
            Notification::createdAt)
        .containsExactly(recipient.userId(), eventId, causeId, text.title(), text.body(), now);
    assertThat(Notification.deduplicationKey(recipient.userId(), UUID.randomUUID()))
        .isNotEqualTo(first.deduplicationKey());
  }
}
