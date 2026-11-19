package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.EventType;
import java.util.List;
import java.util.UUID;

/**
 * A member corrects details of an event that are not an official change. A null field stays as it
 * is.
 *
 * @param ticketEventCode a ticketed event's new code
 * @param accessCode a public event's new access code
 * @param type the event's new type; its code is sent in that type's field
 * @param district the name of the event's new district
 */
public record CorrectEventCommand(
    UUID userId,
    UUID eventId,
    String name,
    String description,
    Integer capacity,
    String ticketEventCode,
    String accessCode,
    List<String> queuePoints,
    ConfirmationsInput confirmations,
    EventType type,
    String district) {

  public CorrectEventCommand {
    queuePoints = queuePoints == null ? null : List.copyOf(queuePoints);
  }
}
