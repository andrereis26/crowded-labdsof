package com.crowded.jobs.weather.domain.repository;

import com.crowded.jobs.weather.domain.exception.WeatherUnavailableException;
import com.crowded.jobs.weather.domain.model.Forecast;
import com.crowded.jobs.weather.domain.model.Warnings;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** IPMA's open data (ADR 0010): weather warnings and forecasts by district. */
public interface WeatherSource {

  /**
   * The warnings of each of {@code districts}. A district whose data is invalid, or that IPMA does
   * not know, is left out and logged, so its last valid warnings stay.
   *
   * @throws WeatherUnavailableException when IPMA does not answer
   */
  Map<String, Warnings> warnings(Set<String> districts);

  /**
   * The forecast of {@code district}'s capital; empty, and logged, when its data is invalid or IPMA
   * does not know the district.
   *
   * @throws WeatherUnavailableException when IPMA does not answer
   */
  Optional<Forecast> forecast(String district);
}
