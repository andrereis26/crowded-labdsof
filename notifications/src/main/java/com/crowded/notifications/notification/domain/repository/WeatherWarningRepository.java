package com.crowded.notifications.notification.domain.repository;

import com.crowded.notifications.notification.domain.model.WeatherWarning;
import java.util.Optional;
import java.util.UUID;

/** The IPMA warnings Core records for its events. */
public interface WeatherWarningRepository {

  /** The warning {@code warningId}, or empty when it does not exist. */
  Optional<WeatherWarning> findById(UUID warningId);
}
