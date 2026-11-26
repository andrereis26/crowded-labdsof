package com.crowded.core.event.domain.model;

import java.util.List;
import java.util.Objects;

/** IPMA's forecast for a district's capital, one entry per day from today. */
public record ForecastSnapshot(District district, List<WeatherForecast> days)
    implements WeatherSnapshot {

  public ForecastSnapshot {
    Objects.requireNonNull(district, "district");
    days = List.copyOf(days);
  }
}
