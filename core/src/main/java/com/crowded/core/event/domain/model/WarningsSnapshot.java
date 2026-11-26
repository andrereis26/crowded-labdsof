package com.crowded.core.event.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Every warning IPMA has for a district; empty when it has none.
 *
 * @param fetchedAt when Jobs fetched them, which participants read as a new warning's time
 */
public record WarningsSnapshot(District district, Instant fetchedAt, List<IssuedWarning> warnings)
    implements WeatherSnapshot {

  public WarningsSnapshot {
    Objects.requireNonNull(district, "district");
    Objects.requireNonNull(fetchedAt, "fetchedAt");
    warnings = List.copyOf(warnings);
  }
}
