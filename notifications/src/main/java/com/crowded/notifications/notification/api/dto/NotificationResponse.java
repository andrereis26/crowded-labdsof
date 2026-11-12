package com.crowded.notifications.notification.api.dto;

import com.crowded.notifications.notification.domain.model.NotificationType;
import java.time.Instant;
import java.util.UUID;

/**
 * A notification in the center or on the live stream (API Outline, Notification center).
 *
 * @param eventName null when the event no longer exists
 * @param causeId null when no change, alert or incident is behind it
 * @param readAt null while unread
 */
public record NotificationResponse(
    UUID id,
    NotificationType type,
    String title,
    String body,
    UUID eventId,
    String eventName,
    UUID causeId,
    Instant createdAt,
    Instant readAt) {}
