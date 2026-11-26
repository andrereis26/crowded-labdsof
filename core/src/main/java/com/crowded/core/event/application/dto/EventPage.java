package com.crowded.core.event.application.dto;

import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.QueuePoint;
import com.crowded.core.event.domain.model.WeatherForecast;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * What a connected participant reads on an event's page: its details, set by its organization, the
 * latest official change, the crowd level, the queue waiting times, the active alerts and IPMA's
 * weather. Stored in the cache as it is, so it holds only plain values.
 *
 * @param doorsOpenAt null when not announced
 * @param endDateTime null when not announced
 * @param latestChange null when the event has no change
 * @param crowd null when no crowd level was published
 * @param queues the queue points with a published waiting time, in order
 * @param activeAlerts the alerts sent and not resolved, the newest first
 * @param forecast IPMA's forecast for the event's days it has, the earliest first
 * @param warnings IPMA's warnings for the district during the event, the most severe first
 * @param imageUpdatedAt when its image was uploaded; null when it has none
 */
public record EventPage(
    UUID id,
    String name,
    String description,
    EventType type,
    EventStatus status,
    Instant startDateTime,
    Instant doorsOpenAt,
    Instant endDateTime,
    String venueName,
    String address,
    String district,
    Double latitude,
    Double longitude,
    UUID organizationId,
    String organizationName,
    EventChangeView latestChange,
    CrowdStatus crowd,
    List<QueuePoint> queues,
    List<ActiveAlert> activeAlerts,
    List<WeatherForecast> forecast,
    List<WeatherWarningView> warnings,
    Instant imageUpdatedAt) {

  public EventPage {
    // A page cached by an earlier release has none.
    queues = queues == null ? List.of() : List.copyOf(queues);
    activeAlerts = activeAlerts == null ? List.of() : List.copyOf(activeAlerts);
    forecast = forecast == null ? List.of() : List.copyOf(forecast);
    warnings = warnings == null ? List.of() : List.copyOf(warnings);
  }
}
