package com.crowded.notifications.notification.domain.repository;

import java.util.UUID;

/**
 * Announces a new notification to every instance of this service, so the one holding its
 * recipient's live stream sends it.
 */
public interface NotificationAnnouncements {

  /**
   * Announces the notification {@code notificationId} once the current transaction commits, or at
   * once without a transaction; nothing is announced when the transaction rolls back. Never throws
   * for a failed announcement: the recipient still finds the notification in the center.
   */
  void announce(UUID notificationId);
}
