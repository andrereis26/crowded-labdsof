package com.crowded.jobs.weather.domain.model;

import java.util.Comparator;
import java.util.List;

/**
 * Every yellow, orange or red warning IPMA has for a district, in a fixed order, so the same
 * warnings fetched twice are equal however IPMA ordered them. Empty when it has none.
 */
public record Warnings(List<Warning> warnings) implements WeatherContent {

  private static final Comparator<Warning> ORDER =
      Comparator.comparing(Warning::awarenessType)
          .thenComparing(Warning::start)
          .thenComparing(Warning::level)
          .thenComparing(Warning::end);

  public Warnings {
    warnings = List.copyOf(warnings.stream().sorted(ORDER).toList());
  }

  @Override
  public SnapshotKind kind() {
    return SnapshotKind.WARNINGS;
  }
}
