package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An event as its organization sees it in the backoffice (API Outline, journey 1). The backoffice
 * draws a public event's QR code from its access code.
 *
 * @param ticketEventCode a ticketed event's code; null for a public one
 * @param accessCode a public event's access code; null for a ticketed one
 * @param capacity null when it was never set
 * @param connectedUsers how many participants are connected to it now
 */
public record ManagedEventResponse(
    UUID id,
    String name,
    String description,
    EventType type,
    EventStatus status,
    String ticketEventCode,
    String accessCode,
    Instant startDateTime,
    Instant doorsOpenAt,
    Instant endDateTime,
    VenueResponse venue,
    Integer capacity,
    long connectedUsers,
    List<String> queuePoints,
    ConfirmationsResponse confirmations) {

  public ManagedEventResponse {
    queuePoints = List.copyOf(queuePoints);
  }
}
