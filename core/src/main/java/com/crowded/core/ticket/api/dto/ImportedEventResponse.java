package com.crowded.core.ticket.api.dto;

import java.time.Instant;
import java.util.UUID;

/** The registered event a ticket was connected to; its data is the organization's. */
public record ImportedEventResponse(
    UUID id, String name, Instant startDateTime, Instant doorsOpenAt, String venueName) {}
