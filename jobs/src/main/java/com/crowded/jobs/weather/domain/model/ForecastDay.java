package com.crowded.jobs.weather.domain.model;

import com.crowded.jobs.weather.domain.exception.InvalidWeatherDataException;
import java.time.LocalDate;

/**
 * IPMA's forecast for one day in a district's capital.
 *
 * @param conditions the kind of weather, such as "Céu limpo"
 * @param precipitationProbability chance of rain, from 0 to 100
 */
public record ForecastDay(
    LocalDate date,
    String conditions,
    double minTemperature,
    double maxTemperature,
    double precipitationProbability) {

  /**
   * @throws InvalidWeatherDataException when the date or the conditions are missing, the minimum is
   *     above the maximum or the chance of rain is not a percentage
   */
  public ForecastDay {
    conditions = conditions == null ? "" : conditions.strip();
    if (date == null || conditions.isEmpty()) {
      throw new InvalidWeatherDataException("A forecast day without its date or conditions");
    }
    if (!(minTemperature <= maxTemperature)) {
      throw new InvalidWeatherDataException("A forecast whose minimum is above its maximum");
    }
    if (!(precipitationProbability >= 0 && precipitationProbability <= 100)) {
      throw new InvalidWeatherDataException("A chance of rain outside 0 to 100");
    }
  }
}
