package com.crowded.core.connection.api.dto;

import java.time.Instant;
import java.util.UUID;

/** The event a connection opens, with what the participant sees before its page loads. */
public record ConnectedEventResponse(
    UUID id, String name, Instant startDateTime, Instant doorsOpenAt, String venueName) {}
