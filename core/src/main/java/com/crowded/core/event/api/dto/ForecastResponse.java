package com.crowded.core.event.api.dto;

import java.time.LocalDate;

/**
 * IPMA's forecast for one day of an event, in its district's capital.
 *
 * @param precipitationProbability chance of rain, from 0 to 100
 * @param provenance IPMA, with the time it updated the forecast
 */
public record ForecastResponse(
    LocalDate date,
    String conditions,
    double minTemperature,
    double maxTemperature,
    double precipitationProbability,
    ProvenanceResponse provenance) {}
