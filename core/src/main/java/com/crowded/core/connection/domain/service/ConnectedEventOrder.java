package com.crowded.core.connection.domain.service;

import com.crowded.core.connection.domain.model.ConnectedEvent;
import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

/**
 * The order a participant sees their events in: those that have not ended come first, the soonest
 * to start first, then those that ended, the latest first.
 */
public final class ConnectedEventOrder {

  private ConnectedEventOrder() {}

  public static List<ConnectedEvent> sort(Collection<ConnectedEvent> events, Instant now) {
    Comparator<ConnectedEvent> byStart = Comparator.comparing(ConnectedEvent::startsAt);
    Comparator<ConnectedEvent> order =
        Comparator.comparing((ConnectedEvent event) -> event.isOver(now))
            .thenComparing(
                (a, b) -> a.isOver(now) ? byStart.reversed().compare(a, b) : byStart.compare(a, b));
    return events.stream().sorted(order).toList();
  }
}
