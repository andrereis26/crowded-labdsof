package com.crowded.core.ticket.domain.repository;

import com.crowded.core.ticket.domain.model.EntryCodeHash;
import com.crowded.core.ticket.domain.model.Ticket;
import com.crowded.core.ticket.domain.model.TicketExtraction;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The participants' tickets, each stored with what the AI read from it. */
public interface TicketRepository {

  /**
   * Stores a new ticket with its extraction, after the connection with the same id.
   *
   * @throws com.crowded.core.shared.domain.exception.ConflictException with {@code
   *     TICKET_ALREADY_IMPORTED} when another ticket has the same entry code hash
   */
  void add(Ticket ticket, TicketExtraction extraction, Instant now);

  /** The ticket with {@code hash}, whoever owns it; empty when there is none. */
  Optional<Ticket> findByEntryCodeHash(EntryCodeHash hash);

  /** Every ticket of {@code ownerId}; empty when they have none. */
  List<Ticket> findByOwner(UUID ownerId);

  /** The ticket that shares its id with a connection; empty when the connection has none. */
  Optional<Ticket> findById(UUID id);
}
