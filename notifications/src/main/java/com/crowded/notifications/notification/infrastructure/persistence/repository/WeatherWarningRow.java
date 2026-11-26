package com.crowded.notifications.notification.infrastructure.persistence.repository;

import java.time.Instant;
import java.util.UUID;

/** A {@code weather_warning} with the district of its event. */
public interface WeatherWarningRow {

  UUID getId();

  UUID getEventId();

  String getAwarenessType();

  String getLevel();

  Instant getStartTime();

  Instant getEndTime();

  String getDescription();

  String getDistrict();
}
