package com.crowded.jobs.weather.domain.model;

import java.util.UUID;

/**
 * Tells Core that a district's weather changed, so it applies the snapshot to the events there (API
 * Outline, Messages). Its message id is the snapshot's id, the same on every publication.
 */
public record WeatherUpdated(String district, UUID snapshotId) {

  /** Routing key on the {@code crowded.events} exchange. */
  public static final String ROUTING_KEY = "weather.updated";
}
