package com.crowded.core.event.api.dto;

import java.util.List;

/**
 * The weather on an event's page, from IPMA.
 *
 * @param forecast the event's days IPMA has a forecast for, the earliest first
 * @param warnings the warnings for its district during the event, the most severe first
 */
public record WeatherResponse(
    List<ForecastResponse> forecast, List<WeatherWarningResponse> warnings) {

  public WeatherResponse {
    forecast = List.copyOf(forecast);
    warnings = List.copyOf(warnings);
  }
}
