package com.crowded.core.connection.application.dto;

import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.domain.model.EventStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * An event in a participant's list.
 *
 * @param doorsOpenAt null when the event does not say
 * @param latestChange null when the event has no change
 * @param imageUpdatedAt when its image was uploaded; null when it has none
 */
public record MyEvent(
    UUID id,
    String name,
    EventStatus status,
    Instant startDateTime,
    Instant doorsOpenAt,
    String venueName,
    EventChangeView latestChange,
    List<ActiveAlert> activeAlerts,
    Instant imageUpdatedAt) {

  public MyEvent {
    activeAlerts = List.copyOf(activeAlerts);
  }
}
