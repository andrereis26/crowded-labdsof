package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import java.time.Instant;
import java.util.UUID;

/**
 * What a connected participant reads on an event's page: its details, set by its organization, the
 * latest official change and the crowd level. Stored in the cache as it is, so it holds only plain
 * values.
 *
 * @param doorsOpenAt null when not announced
 * @param endDateTime null when not announced
 * @param latestChange null when the event has no change
 * @param crowd null when no crowd level was published
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
    CrowdStatus crowd) {}
