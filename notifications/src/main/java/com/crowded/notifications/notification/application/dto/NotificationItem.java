package com.crowded.notifications.notification.application.dto;

import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationType;
import java.time.Instant;
import java.util.UUID;

/**
 * A notification as its recipient sees it in the center and on the live stream.
 *
 * @param eventName the event's name; null when the event no longer exists
 * @param causeId the EventChange, Alert or Incident behind it
 * @param readAt when it was first opened; null if unread
 */
public record NotificationItem(
    UUID id,
    NotificationType type,
    String title,
    String body,
    UUID eventId,
    String eventName,
    UUID causeId,
    Instant createdAt,
    Instant readAt) {

  public static NotificationItem of(Notification notification, String eventName) {
    return new NotificationItem(
        notification.getId(),
        notification.getType(),
        notification.getTitle(),
        notification.getBody(),
        notification.getEventId(),
        eventName,
        notification.getCauseId(),
        notification.getCreatedAt(),
        notification.getReadAt());
  }
}
