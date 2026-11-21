package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response of {@code GET /events/{eventId}} and each update on {@code GET /events/{eventId}/live}:
 * the event's details, all from its organization, its latest change, its crowd level and its queue
 * waiting times.
 *
 * @param latestChange null when the event has no change
 * @param crowd null when no crowd level was published
 * @param queues the queue points with a waiting time, in order
 */
public record EventPageResponse(
    UUID id,
    String name,
    String description,
    EventType type,
    EventStatus status,
    Instant startDateTime,
    Instant doorsOpenAt,
    Instant endDateTime,
    VenueResponse venue,
    OrganizationSummaryResponse organization,
    EventChangeResponse latestChange,
    CrowdResponse crowd,
    List<QueueTimeResponse> queues) {

  public EventPageResponse {
    queues = List.copyOf(queues);
  }
}
