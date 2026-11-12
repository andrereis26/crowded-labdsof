package com.crowded.notifications.notification.application.dto;

import java.util.List;

/**
 * A participant's notification center.
 *
 * @param notifications newest first
 * @param unreadCount how many of them are unread
 */
public record NotificationList(List<NotificationItem> notifications, long unreadCount) {

  public NotificationList {
    notifications = List.copyOf(notifications);
  }
}
