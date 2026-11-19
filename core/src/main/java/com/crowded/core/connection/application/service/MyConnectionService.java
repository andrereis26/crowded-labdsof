package com.crowded.core.connection.application.service;

import com.crowded.core.connection.application.dto.MyConnection;
import com.crowded.core.connection.application.dto.MyConnections;
import com.crowded.core.connection.application.dto.MyEvent;
import com.crowded.core.connection.application.dto.TicketSummary;
import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.connection.domain.model.ConnectedEvent;
import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.domain.repository.ActiveAlertRepository;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.connection.domain.service.ConnectedEventOrder;
import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.ticket.domain.model.Ticket;
import com.crowded.core.ticket.domain.repository.TicketRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lists a participant's events with the latest change and the active alerts of each, reading each
 * of them once for all the events instead of once per event.
 */
@Service
public class MyConnectionService {

  private final ConnectionRepository connections;
  private final EventRepository events;
  private final EventChangeRepository changes;
  private final ActiveAlertRepository alerts;
  private final TicketRepository tickets;
  private final Clock clock;

  MyConnectionService(
      ConnectionRepository connections,
      EventRepository events,
      EventChangeRepository changes,
      ActiveAlertRepository alerts,
      TicketRepository tickets,
      Clock clock) {
    this.connections = connections;
    this.events = events;
    this.changes = changes;
    this.alerts = alerts;
    this.tickets = tickets;
    this.clock = clock;
  }

  /**
   * The events {@code userId} is connected to: those that have not ended first, the soonest first,
   * then those that ended. It reads only that participant's connections.
   */
  @Transactional(readOnly = true)
  public MyConnections listOf(UUID userId) {
    Instant now = clock.instant();
    List<EventConnection> mine = connections.findByUser(userId);
    List<UUID> eventIds = mine.stream().map(EventConnection::getEventId).distinct().toList();
    Map<UUID, Event> eventsById =
        events.findAllById(eventIds).stream()
            .collect(Collectors.toMap(Event::getId, Function.identity()));
    Map<UUID, EventChange> latestChanges = changes.findLatestByEvents(eventIds);
    Map<UUID, List<ActiveAlert>> activeAlerts = alerts.findByEvents(eventIds);
    Map<UUID, Ticket> ticketsById =
        tickets.findByOwner(userId).stream()
            .collect(Collectors.toMap(Ticket::getId, Function.identity()));

    List<ConnectedEvent> connected =
        mine.stream()
            .filter(connection -> eventsById.containsKey(connection.getEventId()))
            .map(
                connection ->
                    new ConnectedEvent(
                        connection,
                        eventsById.get(connection.getEventId()),
                        latestChanges.get(connection.getEventId()),
                        activeAlerts.getOrDefault(connection.getEventId(), List.of()),
                        ticketsById.get(connection.getId())))
            .toList();
    return new MyConnections(
        ConnectedEventOrder.sort(connected, now).stream().map(this::view).toList(), now);
  }

  private MyConnection view(ConnectedEvent connected) {
    Event event = connected.event();
    Schedule schedule = event.getState().schedule();
    EventChange latest = connected.latestChange();
    Ticket ticket = connected.ticket();
    return new MyConnection(
        connected.connection().getId(),
        connected.type(),
        new MyEvent(
            event.getId(),
            event.getProfile().name(),
            event.getState().status(),
            schedule.start(),
            schedule.doorsOpenAt(),
            event.getState().venue().name(),
            latest == null ? null : EventChangeView.of(latest),
            connected.activeAlerts()),
        ticket == null
            ? null
            : new TicketSummary(
                ticket.getDetails().gate(),
                ticket.getDetails().sector(),
                ticket.getDetails().seat(),
                ticket.hasEntryCode()));
  }
}
