package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.WarningLevel;
import java.time.Instant;
import java.util.UUID;

/**
 * An IPMA warning for the event's district during the event.
 *
 * @param awarenessType what it warns of, such as "Vento"
 * @param severity {@code CRITICAL} for red, {@code WARNING} for orange, {@code INFO} for yellow
 * @param description IPMA's text; null when it gave none
 */
public record WeatherWarningResponse(
    UUID id,
    String awarenessType,
    WarningLevel level,
    Severity severity,
    Instant startTime,
    Instant endTime,
    String description,
    ProvenanceResponse provenance) {}
