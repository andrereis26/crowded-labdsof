package com.crowded.core.event.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An operational alert an organization sent to the participants connected to its event. Only {@link
 * Event#sendAlert} creates one; it never changes afterwards.
 */
public final class Alert {

  private final UUID id;
  private final UUID eventId;
  private final UUID organizationId;
  private final AlertType type;
  private final AlertMessage message;
  private final Instant sentAt;
  private final int recipients;

  private Alert(
      UUID id,
      UUID eventId,
      UUID organizationId,
      AlertType type,
      AlertMessage message,
      Instant sentAt,
      int recipients) {
    this.id = Objects.requireNonNull(id, "id");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.organizationId = Objects.requireNonNull(organizationId, "organizationId");
    this.type = Objects.requireNonNull(type, "type");
    this.message = Objects.requireNonNull(message, "message");
    this.sentAt = Objects.requireNonNull(sentAt, "sentAt");
    if (recipients < 0) {
      throw new IllegalArgumentException("recipients must not be negative");
    }
    this.recipients = recipients;
  }

  /** The alert the organization sends now to the {@code recipients} connected participants. */
  static Alert send(
      UUID eventId,
      UUID organizationId,
      AlertType type,
      AlertMessage message,
      int recipients,
      Instant now) {
    return new Alert(UUID.randomUUID(), eventId, organizationId, type, message, now, recipients);
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public UUID getOrganizationId() {
    return organizationId;
  }

  public AlertType getType() {
    return type;
  }

  /** How prominently participants see it, which its type decides. */
  public Severity getSeverity() {
    return type.severity();
  }

  public String getMessage() {
    return message.text();
  }

  public Instant getSentAt() {
    return sentAt;
  }

  /** How many participants were connected to the event when it was sent. */
  public int getRecipients() {
    return recipients;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Alert alert && id.equals(alert.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Alert[" + id + ", " + type + "]";
  }
}
