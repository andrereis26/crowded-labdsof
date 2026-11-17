package com.crowded.core.ticket.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * The registered event a ticket was connected to, as the organization registered it.
 *
 * @param doorsOpenAt null when the event does not say
 */
public record ImportedEvent(
    UUID id, String name, Instant startDateTime, Instant doorsOpenAt, String venueName) {}
