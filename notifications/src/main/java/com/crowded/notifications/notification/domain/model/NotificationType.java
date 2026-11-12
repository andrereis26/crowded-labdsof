package com.crowded.notifications.notification.domain.model;

/** What a notification is about, as {@code notification_type} in the database (API Outline). */
public enum NotificationType {
  OFFICIAL_CHANGE,
  OPERATIONAL_ALERT,
  ORGANIZATION_SURVEY,
  WEATHER_WARNING,
  INCIDENT_STATUS,
  RECOGNITION,
  CONFIRMATION_REQUEST
}
