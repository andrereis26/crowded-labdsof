package com.crowded.notifications.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A notification for one participant. Its content never changes; only when it was first delivered
 * on a live stream and first read are stamped.
 */
public final class Notification {

  private final UUID id;
  private final UUID userId;
  private final UUID eventId;
  private final NotificationType type;
  private final UUID causeId;
  private final NotificationText text;
  private final String deduplicationKey;
  private final Instant createdAt;
  private Instant deliveredAt;
  private Instant readAt;

  private Notification(
      UUID id,
      UUID userId,
      UUID eventId,
      NotificationType type,
      UUID causeId,
      NotificationText text,
      String deduplicationKey,
      Instant createdAt,
      Instant deliveredAt,
      Instant readAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.type = Objects.requireNonNull(type, "type");
    this.causeId = causeId;
    this.text = Objects.requireNonNull(text, "text");
    this.deduplicationKey = Objects.requireNonNull(deduplicationKey, "deduplicationKey");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.deliveredAt = deliveredAt;
    this.readAt = readAt;
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
        Objects.requireNonNull(causeId, "causeId"),
        text,
        deduplicationKey(recipient.userId(), messageId),
        now,
        null,
        null);
  }

  /**
   * A notification as stored.
   *
   * @param causeId the EventChange, Alert or Incident behind it; null when there is none
   * @param deliveredAt when it was first sent on a live stream; null if never
   * @param readAt when its recipient first opened it; null if unread
   */
  public static Notification restore(
      UUID id,
      UUID userId,
      UUID eventId,
      NotificationType type,
      UUID causeId,
      NotificationText text,
      String deduplicationKey,
      Instant createdAt,
      Instant deliveredAt,
      Instant readAt) {
    return new Notification(
        id, userId, eventId, type, causeId, text, deduplicationKey, createdAt, deliveredAt, readAt);
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

  /**
   * The EventChange, Alert or Incident behind it; null for a type without one, such as a
   * recognition.
   */
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

  /** When it was first sent on a live stream; null if never. */
  public Instant getDeliveredAt() {
    return deliveredAt;
  }

  /** When its recipient first opened it; null if unread. */
  public Instant getReadAt() {
    return readAt;
  }

  /** Whether {@code userId} is its recipient: nobody else may see or read it. */
  public boolean isFor(UUID userId) {
    return this.userId.equals(userId);
  }

  public boolean isRead() {
    return readAt != null;
  }

  /** Marks it read at {@code at}, unless it was read before: the first time is kept. */
  public void markRead(Instant at) {
    if (readAt == null) {
      readAt = Objects.requireNonNull(at, "at");
    }
  }

  /** Marks it delivered at {@code at}, unless it was delivered before: the first time is kept. */
  public void markDelivered(Instant at) {
    if (deliveredAt == null) {
      deliveredAt = Objects.requireNonNull(at, "at");
    }
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
