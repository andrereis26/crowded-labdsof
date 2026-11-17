package com.crowded.core.ticket.domain.service;

import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.ticket.domain.model.ConnectedTicket;
import com.crowded.core.ticket.domain.model.EntryCodeHash;
import com.crowded.core.ticket.domain.model.ImportMethod;
import com.crowded.core.ticket.domain.model.MatchMethod;
import com.crowded.core.ticket.domain.model.Ticket;
import com.crowded.core.ticket.domain.model.TicketImport;
import com.crowded.core.ticket.domain.repository.EntryCodeCipher;
import com.crowded.core.ticket.domain.repository.EntryCodeHasher;
import com.crowded.core.ticket.domain.repository.TicketRepository;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;
import java.util.Optional;

/**
 * Connects a participant to the registered event that their ticket's extracted code names: an exact
 * match with a ticketed event that has not ended or been cancelled, never a similar name or date,
 * and never an event the client names. One ticket links to one account.
 */
public final class TicketConnections {

  private final EventRepository events;
  private final ConnectionRepository connections;
  private final TicketRepository tickets;
  private final EntryCodeCipher cipher;
  private final EntryCodeHasher hasher;

  public TicketConnections(
      EventRepository events,
      ConnectionRepository connections,
      TicketRepository tickets,
      EntryCodeCipher cipher,
      EntryCodeHasher hasher) {
    this.events = events;
    this.connections = connections;
    this.tickets = tickets;
    this.cipher = cipher;
    this.hasher = hasher;
  }

  /**
   * Connects {@code user} to the event whose code the import's extraction found; empty when it
   * found none or no such event is registered, and nothing is stored. When the user is connected to
   * the event already, returns that connection and adds nothing.
   *
   * @throws com.crowded.core.shared.domain.exception.NotAllowedException when the user is not an
   *     active participant
   * @throws ConflictException with {@code TICKET_ALREADY_IMPORTED} when another account imported
   *     the same ticket
   */
  public Optional<ConnectedTicket> connectByExtractedCode(
      User user, TicketImport pending, Instant now) {
    EventConnection.checkCanConnect(user);
    Optional<Event> matched =
        eventCode(pending.getExtraction().extractedEventCode())
            .flatMap(code -> events.findUpcomingTicketedByEventCode(code, now));
    if (matched.isEmpty()) {
      return Optional.empty();
    }
    Event event = matched.get();
    EntryCodeHash hash =
        hasher.hash(event.getId(), cipher.decrypt(pending.getEncryptedEntryCode()));
    connections.lockConnectionsOf(user.getId());
    Optional<Ticket> sameTicket = tickets.findByEntryCodeHash(hash);
    if (sameTicket.isPresent()) {
      Ticket ticket = sameTicket.get();
      if (!ticket.isOwnedBy(user.getId())) {
        throw new ConflictException(
            "TICKET_ALREADY_IMPORTED", "This ticket is already in use by another account.");
      }
      return Optional.of(new ConnectedTicket(connectionOf(ticket), event, ticket, false));
    }
    Optional<EventConnection> existing =
        connections.findByUserAndEvent(user.getId(), event.getId());
    if (existing.isPresent()) {
      Ticket own = tickets.findById(existing.get().getId()).orElse(null);
      return Optional.of(new ConnectedTicket(existing.get(), event, own, false));
    }
    EventConnection connection = EventConnection.connectWithTicket(user, event.getId(), now);
    Ticket ticket =
        Ticket.connect(
            connection.getId(),
            user.getId(),
            pending.getExtraction().extractedFields(),
            pending.getEncryptedEntryCode(),
            hash,
            ImportMethod.PDF_UPLOAD,
            MatchMethod.EXTRACTED_CODE);
    connections.addTicketConnection(connection);
    tickets.add(ticket, pending.getExtraction(), now);
    return Optional.of(new ConnectedTicket(connection, event, ticket, true));
  }

  /** A ticket shares its id with its connection. */
  private EventConnection connectionOf(Ticket ticket) {
    return connections
        .findById(ticket.getId())
        .orElseThrow(() -> new IllegalStateException("A ticket always has its connection"));
  }

  /** The code as events keep it; empty when there is none or no event could have it. */
  private static Optional<EventCode> eventCode(String extracted) {
    if (extracted == null) {
      return Optional.empty();
    }
    try {
      return Optional.of(new EventCode(extracted));
    } catch (RuleViolationException e) {
      return Optional.empty();
    }
  }
}
