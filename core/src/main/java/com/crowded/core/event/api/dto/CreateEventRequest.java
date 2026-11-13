package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.EventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Body of {@code POST /events} (API Outline, journey 1).
 *
 * @param ticketEventCode for a {@code TICKETED} event, the code printed on its tickets
 * @param accessCode for a {@code PUBLIC} event, the code its organization gives out
 * @param queuePoints the names of its entrances and queue points, in order
 * @param confirmations null for the default categories and rules
 */
public record CreateEventRequest(
    @Size(max = 120) String name,
    @Size(max = 2000) String description,
    @NotNull EventType type,
    @Size(max = 64) String ticketEventCode,
    @Size(max = 64) String accessCode,
    OffsetDateTime startDateTime,
    OffsetDateTime doorsOpenAt,
    OffsetDateTime endDateTime,
    @NotNull @Valid VenueRequest venue,
    Integer capacity,
    @Size(max = 30) List<@NotNull @Size(max = 60) String> queuePoints,
    @Valid ConfirmationsRequest confirmations) {

  public CreateEventRequest {
    queuePoints = queuePoints == null ? null : List.copyOf(queuePoints);
  }
}
