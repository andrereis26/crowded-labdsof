package com.crowded.core.event.domain.model;

/**
 * What Jobs last fetched from IPMA for a district: its warnings or its forecast (Asynchronous
 * Flows, weather warning flow). Core applies it to the district's events.
 */
public sealed interface WeatherSnapshot permits WarningsSnapshot, ForecastSnapshot {

  District district();
}
