package com.crowded.notifications.notification.domain.repository;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Core's events, read only for the names shown next to a notification. */
public interface EventRepository {

  /** The name of each of {@code eventIds} that exists; an event that does not is left out. */
  Map<UUID, String> findNames(Collection<UUID> eventIds);
}
