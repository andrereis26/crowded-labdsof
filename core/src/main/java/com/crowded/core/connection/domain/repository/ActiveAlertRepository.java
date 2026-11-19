package com.crowded.core.connection.domain.repository;

import com.crowded.core.connection.domain.model.ActiveAlert;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** The alerts the organizations sent about events and have not resolved, to read them. */
public interface ActiveAlertRepository {

  /**
   * The active alerts of each of {@code eventIds}, the newest first; an event without any is not in
   * the map.
   */
  Map<UUID, List<ActiveAlert>> findByEvents(Collection<UUID> eventIds);
}
