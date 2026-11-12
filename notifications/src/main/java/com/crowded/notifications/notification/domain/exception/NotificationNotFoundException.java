package com.crowded.notifications.notification.domain.exception;

import com.crowded.notifications.shared.domain.exception.NotFoundException;
import java.io.Serial;
import java.util.UUID;

/**
 * No notification has this id for the caller. Another participant's notification is reported the
 * same way, so its existence is not revealed.
 */
public class NotificationNotFoundException extends NotFoundException {

  @Serial private static final long serialVersionUID = 1L;

  public NotificationNotFoundException(UUID notificationId) {
    super("NOTIFICATION_NOT_FOUND", "No notification " + notificationId + ".");
  }
}
