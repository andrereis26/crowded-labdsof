package com.crowded.core.ticket.application.service;

import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.ticket.application.dto.ImportedEvent;
import com.crowded.core.ticket.application.dto.TicketImportResult;
import com.crowded.core.ticket.domain.model.ConnectedTicket;
import com.crowded.core.ticket.domain.model.MatchMethod;
import com.crowded.core.ticket.domain.model.Ticket;
import com.crowded.core.ticket.domain.model.TicketDetails;
import com.crowded.core.ticket.domain.model.TicketImport;
import com.crowded.core.ticket.domain.repository.EntryCodeCipher;
import com.crowded.core.ticket.domain.repository.EntryCodeHasher;
import com.crowded.core.ticket.domain.repository.TicketRepository;
import com.crowded.core.ticket.domain.service.TicketConnections;
import com.crowded.core.user.domain.model.User;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.util.Optional;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connects a participant to the registered event of the code extracted from their ticket or typed
 * by them ({@link TicketConnections}) in one transaction, and logs and counts the outcome. The
 * entry code and its hash are never logged.
 */
@Service
public class TicketConnectionService {

  private static final Logger log = LoggerFactory.getLogger(TicketConnectionService.class);

  private final TicketConnections connecting;
  private final Clock clock;
  private final MeterRegistry meters;

  TicketConnectionService(
      EventRepository events,
      ConnectionRepository connections,
      TicketRepository tickets,
      EntryCodeCipher cipher,
      EntryCodeHasher hasher,
      Clock clock,
      MeterRegistry meters) {
    this.connecting = new TicketConnections(events, connections, tickets, cipher, hasher);
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Connects {@code user} to the event that {@code pending}'s extracted code names, and stores the
   * ticket with its extraction. Returns empty when no registered event matches, and then nothing is
   * stored.
   *
   * @throws ConflictException with {@code TICKET_ALREADY_IMPORTED} when another account imported
   *     the same ticket
   */
  @Transactional
  public Optional<TicketImportResult> connectByExtractedCode(User user, TicketImport pending) {
    return connect(
        user,
        MatchMethod.EXTRACTED_CODE,
        () -> connecting.connectByExtractedCode(user, pending, clock.instant()));
  }

  /**
   * Connects {@code user} to the event that the code they typed names, like {@link
   * #connectByExtractedCode}.
   *
   * @throws ConflictException with {@code TICKET_ALREADY_IMPORTED} when another account imported
   *     the same ticket
   */
  @Transactional
  public Optional<TicketImportResult> connectByTypedCode(
      User user, TicketImport pending, String typedCode) {
    return connect(
        user,
        MatchMethod.TYPED_CODE,
        () -> connecting.connectByTypedCode(user, pending, typedCode, clock.instant()));
  }

  private Optional<TicketImportResult> connect(
      User user, MatchMethod method, Supplier<Optional<ConnectedTicket>> match) {
    ConnectedTicket connected;
    try {
      Optional<ConnectedTicket> matched = match.get();
      if (matched.isEmpty()) {
        count(method, "NOT_MATCHED");
        return Optional.empty();
      }
      connected = matched.get();
    } catch (ConflictException e) {
      log.info("Ticket import by user {} refused: the ticket is in use", user.getId());
      count(method, "ALREADY_IMPORTED");
      throw e;
    }
    String outcome = connected.created() ? "CONNECTED" : "ALREADY_CONNECTED";
    log.info(
        "Ticket import by user {} matched event {}: method={}, outcome={}",
        user.getId(),
        connected.event().getId(),
        method,
        outcome);
    count(method, outcome);
    Ticket ticket = connected.ticket();
    return Optional.of(
        TicketImportResult.connected(
            ticket == null ? null : ticket.getId(),
            connected.connection().getId(),
            importedEvent(connected.event()),
            ticket == null ? TicketDetails.NONE : ticket.getDetails()));
  }

  private static ImportedEvent importedEvent(Event event) {
    return new ImportedEvent(
        event.getId(),
        event.getProfile().name(),
        event.getState().schedule().start(),
        event.getState().schedule().doorsOpenAt(),
        event.getState().venue().name());
  }

  /** By method, so the dashboard shows how often the typed code stands in for the AI. */
  private void count(MatchMethod method, String outcome) {
    Counter.builder("crowded.tickets.connection")
        .description("Ticket imports by whether the extracted or typed code matched an event")
        .tag("method", method.name())
        .tag("outcome", outcome)
        .register(meters)
        .increment();
  }
}
