package com.crowded.core.connection;

import com.crowded.core.connection.domain.model.ConnectedEvent;
import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.event.domain.model.Admission;
import com.crowded.core.event.domain.model.Capacity;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.ConfirmationRules;
import com.crowded.core.event.domain.model.ConfirmationSettings;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventProfile;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.QueuePoints;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.ticket.domain.model.EntryCodeHash;
import com.crowded.core.ticket.domain.model.ImportMethod;
import com.crowded.core.ticket.domain.model.MatchMethod;
import com.crowded.core.ticket.domain.model.Ticket;
import com.crowded.core.ticket.domain.model.TicketDetails;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Events, connections and tickets for the tests of the participant's list of events. */
public final class ConnectionTestData {

  public static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

  private ConnectionTestData() {}

  /** A public event that starts {@code hoursFromNow} hours from {@link #NOW} and lasts 3 hours. */
  public static Event eventStartingIn(long hoursFromNow) {
    return eventStartingIn(hoursFromNow, EventStatus.SCHEDULED);
  }

  public static Event eventStartingIn(long hoursFromNow, EventStatus status) {
    Instant start = NOW.plus(hoursFromNow, ChronoUnit.HOURS);
    return Event.restore(
        UUID.randomUUID(),
        UUID.randomUUID(),
        new EventProfile("Arraial " + hoursFromNow, "Arraial popular."),
        new Admission(EventType.PUBLIC, new EventCode("SAOJOAO26")),
        new EventState(
            status,
            new Schedule(start, start.minus(1, ChronoUnit.HOURS), start.plus(3, ChronoUnit.HOURS)),
            new Venue("Fontainhas", "Porto", new Coordinates(41.14, -8.6))),
        new District("Porto"),
        new Capacity(5000),
        QueuePoints.NONE,
        new ConfirmationSettings(Set.of(), ConfirmationRules.DEFAULT),
        null);
  }

  public static EventConnection connectionTo(Event event, UUID userId) {
    return EventConnection.restore(UUID.randomUUID(), userId, event.getId(), NOW);
  }

  public static EventChange cancellationOf(Event event) {
    return EventChange.restore(
        UUID.randomUUID(),
        event.getId(),
        ChangeType.CANCELLATION,
        Severity.CRITICAL,
        Map.of("status", "SCHEDULED"),
        Map.of("status", "CANCELLED"),
        false,
        null,
        "O evento foi cancelado.",
        Provenance.organization("Arraiais", NOW));
  }

  public static Ticket ticketOf(EventConnection connection) {
    return Ticket.restore(
        connection.getId(),
        connection.getUserId(),
        new TicketDetails("A", "2", "14"),
        "encrypted",
        new EntryCodeHash("hash"),
        ImportMethod.PDF_UPLOAD,
        MatchMethod.EXTRACTED_CODE);
  }

  /** The event as the participant's list shows it, without a change, an alert or a ticket. */
  public static ConnectedEvent connected(Event event) {
    return new ConnectedEvent(connectionTo(event, UUID.randomUUID()), event, null, List.of(), null);
  }
}
