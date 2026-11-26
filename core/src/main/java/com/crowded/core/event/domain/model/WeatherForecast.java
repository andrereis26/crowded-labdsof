package com.crowded.core.event.domain.model;

import java.time.LocalDate;
import java.util.Objects;

/**
 * IPMA's forecast for one day of an event, in the capital of its district.
 *
 * @param conditions the kind of weather, such as "Céu limpo"
 * @param precipitationProbability chance of rain, from 0 to 100
 * @param provenance IPMA, with the time it updated the forecast
 */
public record WeatherForecast(
    LocalDate date,
    String conditions,
    double minTemperature,
    double maxTemperature,
    double precipitationProbability,
    Provenance provenance) {

  public WeatherForecast {
    Objects.requireNonNull(date, "date");
    Objects.requireNonNull(conditions, "conditions");
    Objects.requireNonNull(provenance, "provenance");
    if (!(minTemperature <= maxTemperature)) {
      throw new IllegalArgumentException("The minimum temperature is above the maximum");
    }
    if (!(precipitationProbability >= 0 && precipitationProbability <= 100)) {
      throw new IllegalArgumentException("The chance of rain is not a percentage");
    }
  }
}
