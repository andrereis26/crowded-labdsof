package com.crowded.core.event.api.dto;

import com.crowded.core.connection.api.dto.ActiveAlertResponse;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response of {@code GET /events/{eventId}} and each update on {@code GET /events/{eventId}/live}:
 * the event's details, all from its organization, its latest change, its crowd level, its queue
 * waiting times, its active alerts, which a participant sees even with their notifications off, and
 * IPMA's weather.
 *
 * @param latestChange null when the event has no change
 * @param crowd null when no crowd level was published
 * @param queues the queue points with a waiting time, in order
 * @param activeAlerts the alerts sent and not resolved, the newest first
 * @param imageUpdatedAt when its image was uploaded, to fetch it again when it changes; null when
 *     it has none
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
    List<QueueTimeResponse> queues,
    List<ActiveAlertResponse> activeAlerts,
    WeatherResponse weather,
    Instant imageUpdatedAt) {

  public EventPageResponse {
    queues = List.copyOf(queues);
    activeAlerts = List.copyOf(activeAlerts);
  }
}
