package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.WarningLevel;
import com.crowded.core.event.domain.model.WeatherWarning;
import java.time.Instant;
import java.util.UUID;

/**
 * An IPMA warning as participants read it on the event's page, stored in the page cache as it is.
 *
 * @param severity critical for red, a warning for orange, information for yellow
 * @param description IPMA's text; null when it gave none
 * @param provenance IPMA, with the time Crowded saw the warning or its last change
 */
public record WeatherWarningView(
    UUID id,
    String awarenessType,
    WarningLevel level,
    Severity severity,
    Instant startTime,
    Instant endTime,
    String description,
    Provenance provenance) {

  public static WeatherWarningView of(WeatherWarning warning) {
    return new WeatherWarningView(
        warning.getId(),
        warning.getAwarenessType(),
        warning.getLevel(),
        warning.getLevel().severity(),
        warning.getStart(),
        warning.getEnd(),
        warning.getDescription(),
        warning.getProvenance());
  }
}
