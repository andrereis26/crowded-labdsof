package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.EventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A member registers and publishes an event of their organization.
 *
 * @param code the code printed on the tickets of a ticketed event, or a public event's access code
 * @param queuePoints the names of its entrances and queue points, in order
 * @param confirmations null for the defaults
 */
public record RegisterEventCommand(
    UUID userId,
    String name,
    String description,
    EventType type,
    String code,
    Instant startDateTime,
    Instant doorsOpenAt,
    Instant endDateTime,
    VenueInput venue,
    Integer capacity,
    List<String> queuePoints,
    ConfirmationsInput confirmations) {

  public RegisterEventCommand {
    queuePoints = queuePoints == null ? null : List.copyOf(queuePoints);
  }
}
