package com.crowded.jobs.weather.domain.model;

import com.crowded.jobs.weather.domain.exception.InvalidWeatherDataException;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

/**
 * IPMA's forecast for a district's capital, day by day from today.
 *
 * @param updatedAt when IPMA updated it, which participants read as its time
 */
public record Forecast(Instant updatedAt, List<ForecastDay> days) implements WeatherContent {

  /**
   * @throws InvalidWeatherDataException when its update time or every day is missing, or a day
   *     comes twice
   */
  public Forecast {
    if (updatedAt == null || days == null || days.isEmpty()) {
      throw new InvalidWeatherDataException("A forecast without its update time or its days");
    }
    days = List.copyOf(days.stream().sorted(Comparator.comparing(ForecastDay::date)).toList());
    if (days.stream().map(ForecastDay::date).distinct().count() < days.size()) {
      throw new InvalidWeatherDataException("A forecast with the same day twice");
    }
  }

  @Override
  public SnapshotKind kind() {
    return SnapshotKind.FORECAST;
  }
}
