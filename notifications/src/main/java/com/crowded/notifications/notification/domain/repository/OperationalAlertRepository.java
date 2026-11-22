package com.crowded.notifications.notification.domain.repository;

import com.crowded.notifications.notification.domain.model.OperationalAlert;
import java.util.Optional;
import java.util.UUID;

/** The operational alerts Core records. */
public interface OperationalAlertRepository {

  /** The alert {@code alertId}, or empty when it does not exist. */
  Optional<OperationalAlert> findById(UUID alertId);
}
