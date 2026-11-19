package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.EventType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * Body of {@code PATCH /events/{eventId}}: only the fields sent change. The schedule, the venue and
 * a cancellation are official changes, published with {@code POST /events/{eventId}/changes}.
 *
 * @param ticketEventCode a ticketed event's new code; refused for a public event
 * @param accessCode a public event's new access code; refused for a ticketed event
 * @param queuePoints every entrance and queue point, in order; the ones left out are removed
 * @param type the event's new type, which keeps its code unless a new one is sent in that type's
 *     field
 * @param district the event's new district, which selects the weather warnings
 */
public record CorrectEventRequest(
    @Size(max = 120) String name,
    @Size(max = 2000) String description,
    Integer capacity,
    @Size(max = 64) String ticketEventCode,
    @Size(max = 64) String accessCode,
    @Size(max = 30) List<@NotNull @Size(max = 60) String> queuePoints,
    @Valid ConfirmationsRequest confirmations,
    EventType type,
    @Size(max = 60) String district) {

  public CorrectEventRequest {
    queuePoints = queuePoints == null ? null : List.copyOf(queuePoints);
  }
}
