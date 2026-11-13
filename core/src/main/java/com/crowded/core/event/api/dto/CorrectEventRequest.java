package com.crowded.core.event.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code PATCH /events/{eventId}}: only the fields sent change. The schedule, the venue and
 * a cancellation are official changes, published with {@code POST /events/{eventId}/changes}.
 *
 * @param ticketEventCode a ticketed event's new code
 * @param accessCode a public event's new access code
 * @param queuePoints every entrance and queue point, in order; the ones left out are removed
 */
public record CorrectEventRequest(
    @Size(max = 120) String name,
    @Size(max = 2000) String description,
    Integer capacity,
    @Size(max = 64) String ticketEventCode,
    @Size(max = 64) String accessCode,
    @Size(max = 30) List<@NotNull @Size(max = 60) String> queuePoints,
    @Valid ConfirmationsRequest confirmations) {

  public CorrectEventRequest {
    queuePoints = queuePoints == null ? null : List.copyOf(queuePoints);
  }
}
