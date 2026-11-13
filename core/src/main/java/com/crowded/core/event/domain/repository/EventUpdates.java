package com.crowded.core.event.domain.repository;

import java.util.UUID;

/**
 * Tells every instance of Core that what participants see of an event changed, once the current
 * transaction commits: each one drops the stored page and sends the new one on its live streams.
 */
public interface EventUpdates {

  /** Announces that the event's page changed; after the commit when a transaction is open. */
  void announce(UUID eventId);
}
