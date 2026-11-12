package com.crowded.notifications.notification.api.dto;

/** Data of a {@code notification} event on {@code GET /notifications/stream}. */
public record LiveNotificationResponse(NotificationResponse notification, long unreadCount) {}
