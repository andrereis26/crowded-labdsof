package com.crowded.core.connection.application.service;

import static com.crowded.core.connection.ConnectionTestData.NOW;
import static com.crowded.core.connection.ConnectionTestData.cancellationOf;
import static com.crowded.core.connection.ConnectionTestData.connectionTo;
import static com.crowded.core.connection.ConnectionTestData.eventStartingIn;
import static com.crowded.core.connection.ConnectionTestData.ticketOf;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.connection.application.dto.MyConnection;
import com.crowded.core.connection.application.dto.MyConnections;
import com.crowded.core.connection.domain.model.ActiveAlert;
import com.crowded.core.connection.domain.model.ConnectionType;
import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.domain.repository.ActiveAlertRepository;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.ticket.domain.repository.TicketRepository;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MyConnectionServiceTest {

  private static final UUID USER = UUID.randomUUID();

  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final EventRepository events = mock(EventRepository.class);
  private final EventChangeRepository changes = mock(EventChangeRepository.class);
  private final ActiveAlertRepository alerts = mock(ActiveAlertRepository.class);
  private final TicketRepository tickets = mock(TicketRepository.class);
  private final MyConnectionService service =
      new MyConnectionService(
          connections, events, changes, alerts, tickets, Clock.fixed(NOW, ZoneOffset.UTC));

  @Test
  void listsTheEventsInOrderWithTheirChangeAlertsAndTicket() {
    Event soon = eventStartingIn(2);
    Event cancelled = eventStartingIn(30, EventStatus.CANCELLED);
    Event over = eventStartingIn(-48);
    EventConnection soonByTicket = connectionTo(soon, USER);
    EventConnection cancelledByCode = connectionTo(cancelled, USER);
    EventConnection overByCode = connectionTo(over, USER);
    EventChange cancellation = cancellationOf(cancelled);
    ActiveAlert alert =
        new ActiveAlert(
            UUID.randomUUID(),
            "ENTRANCE_CLOSURE",
            Severity.WARNING,
            "Fechada.",
            NOW,
            "Ribeira Live");
    when(connections.findByUser(USER))
        .thenReturn(List.of(overByCode, cancelledByCode, soonByTicket));
    when(events.findAllById(anyCollection())).thenReturn(List.of(cancelled, over, soon));
    when(changes.findLatestByEvents(anyCollection()))
        .thenReturn(Map.of(cancelled.getId(), cancellation));
    when(alerts.findByEvents(anyCollection())).thenReturn(Map.of(soon.getId(), List.of(alert)));
    when(tickets.findByOwner(USER)).thenReturn(List.of(ticketOf(soonByTicket)));

    MyConnections list = service.listOf(USER);

    assertThat(list.syncedAt()).isEqualTo(NOW);
    assertThat(list.connections())
        .extracting(connection -> connection.event().id())
        .containsExactly(soon.getId(), cancelled.getId(), over.getId());
    MyConnection first = list.connections().get(0);
    assertThat(first.connectionId()).isEqualTo(soonByTicket.getId());
    assertThat(first.type()).isEqualTo(ConnectionType.TICKET);
    assertThat(first.ticket().gate()).isEqualTo("A");
    assertThat(first.ticket().hasEntryCode()).isTrue();
    assertThat(first.event().activeAlerts()).containsExactly(alert);
    assertThat(first.event().latestChange()).isNull();
    MyConnection second = list.connections().get(1);
    assertThat(second.type()).isEqualTo(ConnectionType.ACCESS_CODE);
    assertThat(second.ticket()).isNull();
    assertThat(second.event().status()).isEqualTo(EventStatus.CANCELLED);
    assertThat(second.event().latestChange().id()).isEqualTo(cancellation.getId());
    assertThat(second.event().latestChange().severity()).isEqualTo(Severity.CRITICAL);
  }

  @Test
  void readsTheChangesAlertsAndEventsOnceForAllTheEvents() {
    Event first = eventStartingIn(1);
    Event second = eventStartingIn(2);
    when(connections.findByUser(USER))
        .thenReturn(List.of(connectionTo(first, USER), connectionTo(second, USER)));
    when(events.findAllById(anyCollection())).thenReturn(List.of(first, second));
    when(changes.findLatestByEvents(anyCollection())).thenReturn(Map.of());
    when(alerts.findByEvents(anyCollection())).thenReturn(Map.of());
    when(tickets.findByOwner(USER)).thenReturn(List.of());

    service.listOf(USER);

    verify(events, times(1)).findAllById(anyCollection());
    verify(changes, times(1)).findLatestByEvents(anyCollection());
    verify(alerts, times(1)).findByEvents(anyCollection());
    verify(connections).findByUser(USER);
  }

  @Test
  void askingForEachEventOnlyOnceEvenWhenTheParticipantIsConnectedToItTwice() {
    Event event = eventStartingIn(1);
    when(connections.findByUser(USER))
        .thenReturn(List.of(connectionTo(event, USER), connectionTo(event, USER)));
    when(events.findAllById(anyCollection())).thenReturn(List.of(event));
    when(changes.findLatestByEvents(anyCollection())).thenReturn(Map.of());
    when(alerts.findByEvents(anyCollection())).thenReturn(Map.of());
    when(tickets.findByOwner(USER)).thenReturn(List.of());

    MyConnections list = service.listOf(USER);

    assertThat(list.connections()).hasSize(2);
    @SuppressWarnings("unchecked")
    org.mockito.ArgumentCaptor<Collection<UUID>> ids =
        org.mockito.ArgumentCaptor.forClass(Collection.class);
    verify(events).findAllById(ids.capture());
    assertThat(ids.getValue()).containsExactly(event.getId());
  }

  @Test
  void aParticipantWithoutConnectionsHasAnEmptyList() {
    when(connections.findByUser(USER)).thenReturn(List.of());
    when(events.findAllById(anyCollection())).thenReturn(List.of());
    when(changes.findLatestByEvents(anyCollection())).thenReturn(Map.of());
    when(alerts.findByEvents(anyCollection())).thenReturn(Map.of());
    when(tickets.findByOwner(USER)).thenReturn(List.of());

    MyConnections list = service.listOf(USER);

    assertThat(list.connections()).isEmpty();
    assertThat(list.syncedAt()).isEqualTo(NOW);
  }

  @Test
  void leavesOutAConnectionWhoseEventIsGone() {
    Event gone = eventStartingIn(1);
    Event there = eventStartingIn(2);
    when(connections.findByUser(USER))
        .thenReturn(List.of(connectionTo(gone, USER), connectionTo(there, USER)));
    when(events.findAllById(anyCollection())).thenReturn(List.of(there));
    when(changes.findLatestByEvents(anyCollection())).thenReturn(Map.of());
    when(alerts.findByEvents(anyCollection())).thenReturn(Map.of());
    when(tickets.findByOwner(USER)).thenReturn(List.of());

    assertThat(service.listOf(USER).connections())
        .extracting(connection -> connection.event().id())
        .containsExactly(there.getId());
  }
}
