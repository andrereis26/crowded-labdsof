package com.crowded.core.outbox;

import java.util.List;
import java.util.UUID;

/**
 * Asks Notifications to notify the participants connected to an event or, when {@code userIds} is
 * not empty, only those users (API Outline, Messages).
 *
 * @param messageId id of the message, the same on every redelivery
 * @param type notification type, such as {@code OFFICIAL_CHANGE}
 * @param eventId the event
 * @param causeId the EventChange, Alert or Incident behind it
 * @param userIds the recipients; empty for every connected participant
 */
public record NotificationRequested(
    UUID messageId, String type, UUID eventId, UUID causeId, List<UUID> userIds) {

  /** Routing key on the {@code crowded.events} exchange. */
  public static final String ROUTING_KEY = "notification.requested";

  public NotificationRequested {
    userIds = List.copyOf(userIds);
  }
}
