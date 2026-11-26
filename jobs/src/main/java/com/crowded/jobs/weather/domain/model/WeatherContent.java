package com.crowded.jobs.weather.domain.model;

/** What a snapshot holds: a district's warnings or its forecast. */
public sealed interface WeatherContent permits Warnings, Forecast {

  /** Which of the two it is. */
  SnapshotKind kind();
}
