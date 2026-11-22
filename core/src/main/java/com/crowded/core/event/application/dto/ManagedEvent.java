package com.crowded.core.event.application.dto;

import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.event.domain.model.Event;
import java.time.Instant;
import java.util.List;

/**
 * An event as its organization sees it in the backoffice.
 *
 * @param connectedUsers how many participants are connected to it now
 * @param imageUpdatedAt when its image was uploaded; null when it has none
 * @param activeAlerts the alerts sent and not resolved, the newest first
 */
public record ManagedEvent(
    Event event, long connectedUsers, Instant imageUpdatedAt, List<ActiveAlert> activeAlerts) {

  public ManagedEvent {
    activeAlerts = List.copyOf(activeAlerts);
  }
}
