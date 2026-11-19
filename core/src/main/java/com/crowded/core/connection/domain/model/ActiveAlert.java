package com.crowded.core.connection.domain.model;

import com.crowded.core.event.domain.model.Severity;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An alert the organization sent about an event that it has not resolved yet.
 *
 * @param alertId the alert's identifier
 * @param type the kind of alert, as the organization recorded it
 */
public record ActiveAlert(
    UUID alertId, String type, Severity severity, String message, Instant sentAt) {

  public ActiveAlert {
    Objects.requireNonNull(alertId, "alertId");
    Objects.requireNonNull(type, "type");
    Objects.requireNonNull(severity, "severity");
    Objects.requireNonNull(message, "message");
    Objects.requireNonNull(sentAt, "sentAt");
  }
}
