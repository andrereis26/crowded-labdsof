package com.crowded.core.ticket.domain.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Admission;
import com.crowded.core.event.domain.model.Capacity;
import com.crowded.core.event.domain.model.ConfirmationRules;
import com.crowded.core.event.domain.model.ConfirmationSettings;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.model.EventProfile;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.QueuePoints;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.ticket.domain.model.ConnectedTicket;
import com.crowded.core.ticket.domain.model.EntryCodeHash;
import com.crowded.core.ticket.domain.model.ImportMethod;
import com.crowded.core.ticket.domain.model.MatchMethod;
import com.crowded.core.ticket.domain.model.Ticket;
import com.crowded.core.ticket.domain.model.TicketDetails;
import com.crowded.core.ticket.domain.model.TicketExtraction;
import com.crowded.core.ticket.domain.model.TicketImport;
import com.crowded.core.ticket.domain.repository.EntryCodeCipher;
import com.crowded.core.ticket.domain.repository.EntryCodeHasher;
import com.crowded.core.ticket.domain.repository.TicketRepository;
import com.crowded.core.user.domain.model.Role;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class TicketConnectionsTest {

  private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");
  private static final Instant START = NOW.plus(12, ChronoUnit.DAYS);
  private static final User PARTICIPANT =
      User.restore(UUID.randomUUID(), null, Role.PARTICIPANT, true);
  private static final User MEMBER =
      User.restore(UUID.randomUUID(), UUID.randomUUID(), Role.ORGANIZATION_MEMBER, true);
  private static final Event FESTIVAL =
      Event.restore(
          UUID.randomUUID(),
          UUID.randomUUID(),
          new EventProfile("Festival Margens", "Dois dias de música."),
          new Admission(EventType.TICKETED, new EventCode("MARG-2026")),
          new EventState(
              EventStatus.SCHEDULED,
              new Schedule(
                  START, START.minus(2, ChronoUnit.HOURS), START.plus(5, ChronoUnit.HOURS)),
              new Venue("Cais da Ribeira", "Porto", new Coordinates(41.14, -8.61))),
          new District("Porto"),
          new Capacity(8000),
          QueuePoints.NONE,
          new ConfirmationSettings(Set.of(), ConfirmationRules.DEFAULT));
  private static final String ENCRYPTED = "encrypted-code";
  private static final TicketDetails DETAILS = new TicketDetails("A", null, "14");

  private final EventRepository events = mock(EventRepository.class);
  private final ConnectionRepository connections = mock(ConnectionRepository.class);
  private final TicketRepository tickets = mock(TicketRepository.class);
  private final EntryCodeCipher cipher = mock(EntryCodeCipher.class);
  private final EntryCodeHasher hasher = (eventId, code) -> new EntryCodeHash(eventId + "|" + code);
  private final TicketConnections connecting =
      new TicketConnections(events, connections, tickets, cipher, hasher);

  {
    when(cipher.decrypt(ENCRYPTED)).thenReturn("QR-1");
  }

  private static TicketImport pending(String eventCode) {
    return TicketImport.start(
        PARTICIPANT.getId(), ENCRYPTED, new TicketExtraction(eventCode, DETAILS));
  }

  private void festivalIsRegistered() {
    when(events.findUpcomingTicketedByEventCode(new EventCode("MARG-2026"), NOW))
        .thenReturn(Optional.of(FESTIVAL));
  }

  private static Ticket ticketOf(UUID owner) {
    return Ticket.restore(
        UUID.randomUUID(),
        owner,
        DETAILS,
        ENCRYPTED,
        new EntryCodeHash(FESTIVAL.getId() + "|QR-1"),
        ImportMethod.PDF_UPLOAD,
        MatchMethod.EXTRACTED_CODE);
  }

  @Test
  void connectsToTheEventOfTheExtractedCodeAndStoresTheTicket() {
    festivalIsRegistered();
    TicketImport pending = pending(" marg-2026 ");

    ConnectedTicket connected =
        connecting.connectByExtractedCode(PARTICIPANT, pending, NOW).orElseThrow();

    assertThat(connected.created()).isTrue();
    assertThat(connected.event()).isSameAs(FESTIVAL);
    assertThat(connected.connection().getUserId()).isEqualTo(PARTICIPANT.getId());
    assertThat(connected.connection().getEventId()).isEqualTo(FESTIVAL.getId());
    Ticket ticket = connected.ticket();
    assertThat(ticket.getId()).isEqualTo(connected.connection().getId());
    assertThat(ticket.isOwnedBy(PARTICIPANT.getId())).isTrue();
    assertThat(ticket.getDetails()).isEqualTo(DETAILS);
    assertThat(ticket.getEncryptedEntryCode()).isEqualTo(ENCRYPTED);
    assertThat(ticket.getEntryCodeHash()).isEqualTo(new EntryCodeHash(FESTIVAL.getId() + "|QR-1"));
    assertThat(ticket.getImportMethod()).isEqualTo(ImportMethod.PDF_UPLOAD);
    assertThat(ticket.getMatchMethod()).isEqualTo(MatchMethod.EXTRACTED_CODE);
    InOrder order = inOrder(connections, tickets);
    order.verify(connections).lockConnectionsOf(PARTICIPANT.getId());
    order.verify(connections).addTicketConnection(connected.connection());
    order.verify(tickets).add(ticket, pending.getExtraction(), NOW);
  }

  @Test
  void connectsToNothingWhenTheAiFoundNoCode() {
    assertThat(connecting.connectByExtractedCode(PARTICIPANT, pending(null), NOW)).isEmpty();

    verifyNoInteractions(events, connections, tickets);
  }

  @Test
  void connectsToNothingWhenTheCodeCannotBeAnEventCode() {
    assertThat(connecting.connectByExtractedCode(PARTICIPANT, pending("the festival"), NOW))
        .isEmpty();

    verifyNoInteractions(events, connections, tickets);
  }

  @Test
  void connectsToNothingWhenNoRegisteredEventHasTheCode() {
    assertThat(connecting.connectByExtractedCode(PARTICIPANT, pending("OTHER-2026"), NOW))
        .isEmpty();

    verifyNoInteractions(connections, tickets);
  }

  @Test
  void givesTheSameConnectionWhenTheOwnerImportsTheTicketAgain() {
    festivalIsRegistered();
    Ticket own = ticketOf(PARTICIPANT.getId());
    EventConnection connection =
        EventConnection.restore(own.getId(), PARTICIPANT.getId(), FESTIVAL.getId(), NOW);
    when(tickets.findByEntryCodeHash(own.getEntryCodeHash())).thenReturn(Optional.of(own));
    when(connections.findById(own.getId())).thenReturn(Optional.of(connection));

    ConnectedTicket connected =
        connecting.connectByExtractedCode(PARTICIPANT, pending("MARG-2026"), NOW).orElseThrow();

    assertThat(connected.created()).isFalse();
    assertThat(connected.ticket()).isSameAs(own);
    assertThat(connected.connection()).isSameAs(connection);
    verify(connections, never()).addTicketConnection(any());
    verify(tickets, never()).add(any(), any(), any());
  }

  @Test
  void refusesATicketThatAnotherAccountImported() {
    festivalIsRegistered();
    Ticket theirs = ticketOf(UUID.randomUUID());
    when(tickets.findByEntryCodeHash(theirs.getEntryCodeHash())).thenReturn(Optional.of(theirs));

    assertThatThrownBy(
            () -> connecting.connectByExtractedCode(PARTICIPANT, pending("MARG-2026"), NOW))
        .isInstanceOfSatisfying(
            ConflictException.class,
            e -> assertThat(e.getCode()).isEqualTo("TICKET_ALREADY_IMPORTED"));

    verify(connections, never()).addTicketConnection(any());
    verify(tickets, never()).add(any(), any(), any());
  }

  @Test
  void addsNothingWhenTheParticipantIsConnectedToTheEventAlready() {
    festivalIsRegistered();
    EventConnection byAccessCode =
        EventConnection.restore(UUID.randomUUID(), PARTICIPANT.getId(), FESTIVAL.getId(), NOW);
    when(connections.findByUserAndEvent(PARTICIPANT.getId(), FESTIVAL.getId()))
        .thenReturn(Optional.of(byAccessCode));

    ConnectedTicket connected =
        connecting.connectByExtractedCode(PARTICIPANT, pending("MARG-2026"), NOW).orElseThrow();

    assertThat(connected.created()).isFalse();
    assertThat(connected.connection()).isSameAs(byAccessCode);
    assertThat(connected.ticket()).isNull();
    verify(connections, never()).addTicketConnection(any());
    verify(tickets, never()).add(any(), any(), any());
  }

  @Test
  void letsOnlyAParticipantConnectBeforeLookingAtTheCode() {
    assertThatThrownBy(() -> connecting.connectByExtractedCode(MEMBER, pending("MARG-2026"), NOW))
        .isInstanceOfSatisfying(
            NotAllowedException.class, e -> assertThat(e.getCode()).isEqualTo("PARTICIPANT_ONLY"));

    verifyNoInteractions(events, connections, tickets);
  }
}
