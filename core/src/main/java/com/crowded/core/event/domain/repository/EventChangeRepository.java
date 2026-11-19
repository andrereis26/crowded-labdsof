package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventChange;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Official changes. */
public interface EventChangeRepository {

  void save(EventChange change);

  /** The latest change of this type to the event. */
  Optional<EventChange> findLatest(UUID eventId, ChangeType type);

  /** The event's latest change of any type. */
  Optional<EventChange> findLatest(UUID eventId);

  /** The latest change of each of {@code eventIds}; an event without a change is not in the map. */
  Map<UUID, EventChange> findLatestByEvents(Collection<UUID> eventIds);

  /** Every change to the event, the newest first; empty when it has none. */
  List<EventChange> findByEvent(UUID eventId);
}
