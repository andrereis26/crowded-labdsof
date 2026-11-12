package com.crowded.notifications.notification.domain.repository;

import com.crowded.notifications.notification.domain.model.OfficialChange;
import java.util.Optional;
import java.util.UUID;

/** The official changes Core records. */
public interface OfficialChangeRepository {

  /** The change {@code changeId}, or empty when it does not exist. */
  Optional<OfficialChange> findById(UUID changeId);
}
