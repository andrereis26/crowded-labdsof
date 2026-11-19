package com.crowded.core.connection.domain.model;

import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.ticket.domain.model.Ticket;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * What a participant sees of one of their connections: the event, its latest official change and
 * alerts still active, and the ticket when the connection was made with one.
 *
 * @param latestChange null when the event has no change
 * @param ticket null for a connection made with an access code
 */
public record ConnectedEvent(
    EventConnection connection,
    Event event,
    EventChange latestChange,
    List<ActiveAlert> activeAlerts,
    Ticket ticket) {

  public ConnectedEvent {
    Objects.requireNonNull(connection, "connection");
    Objects.requireNonNull(event, "event");
    activeAlerts = List.copyOf(activeAlerts);
  }

  /** A connection that has a ticket was made with it; any other was made with an access code. */
  public ConnectionType type() {
    return ticket == null ? ConnectionType.ACCESS_CODE : ConnectionType.TICKET;
  }

  /** Whether the event has ended by {@code now}, or started already when it says no end. */
  public boolean isOver(Instant now) {
    var schedule = event.getState().schedule();
    Instant last = schedule.end() == null ? schedule.start() : schedule.end();
    return last.isBefore(now);
  }

  public Instant startsAt() {
    return event.getState().schedule().start();
  }
}
