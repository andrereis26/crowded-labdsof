package com.crowded.notifications.notification.domain.repository;

import com.crowded.notifications.notification.domain.model.Notification;

/** The notifications this service writes. */
public interface NotificationRepository {

  /**
   * Stores {@code notification} unless one with its deduplication key exists (NFR-003).
   *
   * @return whether it was stored
   */
  boolean addIfAbsent(Notification notification);
}
