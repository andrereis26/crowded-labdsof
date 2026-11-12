package com.crowded.notifications.notification.application.dto;

import com.crowded.notifications.notification.domain.model.NotificationType;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Notify the participants connected to an event, as asked by Core's {@code NotificationRequested}
 * message (API Outline, Messages).
 *
 * @param messageId id of the message, the same on every redelivery
 * @param causeId the EventChange, Alert or Incident behind it
 * @param userIds the recipients; empty for every connected participant
 */
public record NotifyParticipantsCommand(
    UUID messageId, NotificationType type, UUID eventId, UUID causeId, List<UUID> userIds) {

  public NotifyParticipantsCommand {
    Objects.requireNonNull(messageId, "messageId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(eventId, "eventId");
    Objects.requireNonNull(causeId, "causeId");
    userIds = userIds == null ? List.of() : List.copyOf(userIds);
  }
}
