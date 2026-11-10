package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.Event;
import java.util.Optional;
import java.util.UUID;

/** Events. */
public interface EventRepository {

  /**
   * The event, locked until the transaction ends, so two changes to one event are decided one after
   * the other and a resent change finds the first one applied.
   */
  Optional<Event> findByIdForUpdate(UUID id);

  /** Stores the event's new state. */
  void save(Event event);
}
