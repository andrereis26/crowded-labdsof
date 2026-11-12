package com.crowded.notifications.notification.domain.exception;

import com.crowded.notifications.shared.domain.exception.DomainException;
import java.io.Serial;
import java.util.UUID;

/** The change, alert or incident behind a notification is not in the event it names. */
public class CauseNotFoundException extends DomainException {

  @Serial private static final long serialVersionUID = 1L;

  public CauseNotFoundException(UUID eventId, UUID causeId) {
    super("CAUSE_NOT_FOUND", "Event " + eventId + " has no change " + causeId + ".");
  }
}
