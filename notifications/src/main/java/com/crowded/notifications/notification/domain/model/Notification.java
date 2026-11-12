package com.crowded.notifications.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A notification for one participant. Created once, never changed. */
public final class Notification {

  private final UUID id;
  private final UUID userId;
  private final UUID eventId;
  private final NotificationType type;
  private final UUID causeId;
  private final NotificationText text;
  private final String deduplicationKey;
  private final Instant createdAt;

  private Notification(
      UUID id,
      UUID userId,
      UUID eventId,
      NotificationType type,
      UUID causeId,
      NotificationText text,
      String deduplicationKey,
      Instant createdAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.type = Objects.requireNonNull(type, "type");
    this.causeId = Objects.requireNonNull(causeId, "causeId");
    this.text = Objects.requireNonNull(text, "text");
    this.deduplicationKey = Objects.requireNonNull(deduplicationKey, "deduplicationKey");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
  }

  /**
   * The notification of {@code recipient} asked for by the message {@code messageId}.
   *
   * @param causeId the EventChange, Alert or Incident behind it
   */
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
        text,
        deduplicationKey(recipient.userId(), messageId),
        now);
  }

  /**
   * The key of the notification of {@code userId} asked for by the message {@code messageId}:
   * unique, so a message delivered twice notifies once.
   */
  public static String deduplicationKey(UUID userId, UUID messageId) {
    return userId + ":" + messageId;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public NotificationType getType() {
    return type;
  }

  /** The EventChange, Alert or Incident behind it. */
  public UUID getCauseId() {
    return causeId;
  }

  public String getTitle() {
    return text.title();
  }

  public String getBody() {
    return text.body();
  }

  public String getDeduplicationKey() {
    return deduplicationKey;
  }

  public Instant getCreatedAt() {
    return createdAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Notification notification && id.equals(notification.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Notification[" + id + ", " + type + " for " + userId + "]";
  }
}
