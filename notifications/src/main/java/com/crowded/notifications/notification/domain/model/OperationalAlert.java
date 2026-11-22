package com.crowded.notifications.notification.domain.model;

import java.util.Objects;
import java.util.UUID;

/**
 * An operational alert an organization sent through Core. Every participant reads the same text:
 * the kind of alert as the title and the organization's message as the body. Notifications only
 * reads it: Core owns it.
 */
public final class OperationalAlert {

  private final UUID id;
  private final UUID eventId;
  private final AlertType type;
  private final String message;

  private OperationalAlert(UUID id, UUID eventId, AlertType type, String message) {
    this.id = Objects.requireNonNull(id, "id");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.type = Objects.requireNonNull(type, "type");
    this.message = Objects.requireNonNull(message, "message");
  }

  /** The alert as Core stored it. */
  public static OperationalAlert restore(UUID id, UUID eventId, AlertType type, String message) {
    return new OperationalAlert(id, eventId, type, message);
  }

  /** Whether this alert is about the event {@code event}. */
  public boolean isOf(UUID event) {
    return eventId.equals(event);
  }

  /** What the notification of this alert tells every participant. */
  public NotificationText describe() {
    return new NotificationText(type.title(), message.strip());
  }

  public UUID getId() {
    return id;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OperationalAlert alert && id.equals(alert.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "OperationalAlert[" + id + ", " + type + "]";
  }
}
