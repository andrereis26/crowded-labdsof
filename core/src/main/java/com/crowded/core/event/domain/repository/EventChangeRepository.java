package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventChange;
import java.util.Optional;
import java.util.UUID;

/** Official changes. */
public interface EventChangeRepository {

  void save(EventChange change);

  /** The latest change of this type to the event. */
  Optional<EventChange> findLatest(UUID eventId, ChangeType type);
}
