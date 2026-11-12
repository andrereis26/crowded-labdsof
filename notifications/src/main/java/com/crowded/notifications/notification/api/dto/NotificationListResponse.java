package com.crowded.notifications.notification.api.dto;

import java.util.List;

/** Answer of {@code GET /notifications}: newest first, and how many are unread. */
public record NotificationListResponse(List<NotificationResponse> notifications, long unreadCount) {

  public NotificationListResponse {
    notifications = List.copyOf(notifications);
  }
}
