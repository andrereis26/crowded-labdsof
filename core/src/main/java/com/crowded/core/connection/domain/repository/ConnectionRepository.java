package com.crowded.core.connection.domain.repository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** The participants' connections to events, by a ticket or an access code. */
public interface ConnectionRepository {

  /** Whether {@code userId} is connected to {@code eventId} now; a removed connection is not. */
  boolean isConnected(UUID userId, UUID eventId);

  /**
   * How many participants are connected to each of {@code eventIds}; an event without any is not in
   * the map.
   */
  Map<UUID, Long> countByEvent(Collection<UUID> eventIds);
}
