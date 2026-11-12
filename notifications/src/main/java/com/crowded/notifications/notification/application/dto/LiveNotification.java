package com.crowded.notifications.notification.application.dto;

import java.util.Objects;

/**
 * A new notification sent on its recipient's live stream, with their unread count after it.
 *
 * @param unreadCount how many of the recipient's notifications are unread, this one included
 */
public record LiveNotification(NotificationItem notification, long unreadCount) {

  public LiveNotification {
    Objects.requireNonNull(notification, "notification");
  }
}
