package com.crowded.core.connection.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The participant's connection and the event it opens.
 *
 * @param created false when the participant was already connected, so nothing was added
 * @param doorsOpenAt null when the event does not say
 */
public record JoinedEvent(
    UUID connectionId,
    boolean created,
    UUID eventId,
    String name,
    Instant startDateTime,
    Instant doorsOpenAt,
    String venueName) {}
