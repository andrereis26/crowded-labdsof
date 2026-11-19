package com.crowded.core.connection.api.dto;

import com.crowded.core.event.api.dto.EventChangeResponse;
import com.crowded.core.event.domain.model.EventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An event in {@code GET /me/connections}.
 *
 * @param doorsOpenAt null when the event does not say
 * @param latestChange null when the event has no change
 */
public record MyEventResponse(
    UUID id,
    String name,
    EventStatus status,
    Instant startDateTime,
    Instant doorsOpenAt,
    String venueName,
    EventChangeResponse latestChange,
    List<ActiveAlertResponse> activeAlerts) {

  public MyEventResponse {
    activeAlerts = List.copyOf(activeAlerts);
  }
}
