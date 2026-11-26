package com.crowded.jobs.weather.domain.model;

import com.crowded.jobs.weather.domain.exception.InvalidWeatherDataException;
import java.time.Instant;

/**
 * A weather warning IPMA issued for a district.
 *
 * @param awarenessType what it warns of, as IPMA names it, such as "Vento"
 * @param description IPMA's text, such as "Rajadas até 90 km/h."; null when it gave none
 */
public record Warning(
    String awarenessType, WarningLevel level, Instant start, Instant end, String description) {

  /**
   * @throws InvalidWeatherDataException when the type, the level or a time is missing, or it ends
   *     before it starts
   */
  public Warning {
    awarenessType = awarenessType == null ? "" : awarenessType.strip();
    if (awarenessType.isEmpty()) {
      throw new InvalidWeatherDataException("A warning without its type");
    }
    if (level == null || start == null || end == null) {
      throw new InvalidWeatherDataException("A warning without its level or its times");
    }
    if (end.isBefore(start)) {
      throw new InvalidWeatherDataException("A warning that ends before it starts");
    }
    description = description == null || description.isBlank() ? null : description.strip();
  }
}
