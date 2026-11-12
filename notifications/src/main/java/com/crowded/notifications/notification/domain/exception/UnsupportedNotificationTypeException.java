package com.crowded.notifications.notification.domain.exception;

import com.crowded.notifications.notification.domain.model.NotificationType;
import com.crowded.notifications.shared.domain.exception.DomainException;
import java.io.Serial;

/**
 * Notifications cannot yet write a notification of this type. Retrying does not help: the message
 * waits in the dead-letter queue until the type is supported, then is replayed.
 */
public class UnsupportedNotificationTypeException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public UnsupportedNotificationTypeException(NotificationType type) {
    super("UNSUPPORTED_NOTIFICATION_TYPE", "No notification text for the type " + type + " yet.");
  }
}
