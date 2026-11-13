package com.crowded.core.event.application.dto;

import java.util.List;
import java.util.UUID;

/**
 * A member corrects details of an event that are not an official change. A null field stays as it
 * is.
 *
 * @param ticketEventCode a ticketed event's new code
 * @param accessCode a public event's new access code
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
    ConfirmationsInput confirmations) {

  public CorrectEventCommand {
    queuePoints = queuePoints == null ? null : List.copyOf(queuePoints);
  }
}
