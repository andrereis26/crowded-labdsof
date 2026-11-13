package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventChange;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Official changes. */
public interface EventChangeRepository {

  void save(EventChange change);

  /** The latest change of this type to the event. */
  Optional<EventChange> findLatest(UUID eventId, ChangeType type);

  /** The event's latest change of any type. */
  Optional<EventChange> findLatest(UUID eventId);

  /** Every change to the event, the newest first; empty when it has none. */
  List<EventChange> findByEvent(UUID eventId);
}
