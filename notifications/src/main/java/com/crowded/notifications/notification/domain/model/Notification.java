package com.crowded.notifications.notification.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A notification for one participant (FR-067).
 *
 * @param causeId the EventChange, Alert or Incident behind it
 * @param deduplicationKey the recipient and the message that asked for it, unique, so a message
 *     delivered twice notifies once (NFR-003)
 */
public record Notification(
    UUID id,
    UUID userId,
    UUID eventId,
    NotificationType type,
    UUID causeId,
    String title,
    String body,
    String deduplicationKey,
    Instant createdAt) {

  /** The notification of {@code recipient} asked for by the message {@code messageId}. */
  public static Notification create(
      UUID messageId,
      Recipient recipient,
      UUID eventId,
      NotificationType type,
      UUID causeId,
      NotificationText text,
      Instant now) {
    return new Notification(
        UUID.randomUUID(),
        recipient.userId(),
        eventId,
        type,
        causeId,
        text.title(),
        text.body(),
        deduplicationKey(recipient.userId(), messageId),
        now);
  }

  /** The key of the notification of {@code userId} asked for by the message {@code messageId}. */
  public static String deduplicationKey(UUID userId, UUID messageId) {
    return userId + ":" + messageId;
  }
}
