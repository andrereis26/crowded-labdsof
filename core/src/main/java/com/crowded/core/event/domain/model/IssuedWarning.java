package com.crowded.core.event.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * A warning IPMA has for a district, as Jobs fetched it.
 *
 * @param awarenessType what it warns of, such as "Vento"
 * @param description IPMA's text; null when it gave none
 */
public record IssuedWarning(
    String awarenessType, WarningLevel level, Instant start, Instant end, String description) {

  public IssuedWarning {
    Objects.requireNonNull(awarenessType, "awarenessType");
    Objects.requireNonNull(level, "level");
    Objects.requireNonNull(start, "start");
    Objects.requireNonNull(end, "end");
    if (end.isBefore(start)) {
      throw new IllegalArgumentException("The warning ends before it starts");
    }
  }

  /** Whether {@code warning} is this one: IPMA keeps its type and start when it changes it. */
  boolean isSameAs(WeatherWarning warning) {
    return awarenessType.equals(warning.getAwarenessType()) && start.equals(warning.getStart());
  }
}
