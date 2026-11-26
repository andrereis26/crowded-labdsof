package com.crowded.jobs.weather.domain.repository;

import com.crowded.jobs.weather.domain.model.WeatherUpdated;

/** Publishes {@link WeatherUpdated} to Core. */
public interface WeatherUpdates {

  /** Publishes the message and says whether RabbitMQ confirmed it; false to try again later. */
  boolean publish(WeatherUpdated message);
}
