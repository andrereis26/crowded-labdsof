package com.crowded.core.ticket.infrastructure.persistence.repository;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.infrastructure.persistence.JsonColumnConverter;
import com.crowded.core.ticket.domain.model.EntryCodeHash;
import com.crowded.core.ticket.domain.model.Ticket;
import com.crowded.core.ticket.domain.model.TicketExtraction;
import com.crowded.core.ticket.domain.repository.TicketRepository;
import com.crowded.core.ticket.infrastructure.persistence.entity.TicketEntity;
import com.crowded.core.ticket.infrastructure.persistence.mapper.TicketPersistenceMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Repository;

/** {@link TicketRepository} on PostgreSQL. */
@Repository
class TicketRepositoryAdapter implements TicketRepository {

  private static final String ENTRY_CODE_HASH_CONSTRAINT = "ticket_entry_code_hash_key";

  private final SpringDataTicketRepository tickets;
  private final SpringDataTicketExtractionRepository extractions;
  private final TicketPersistenceMapper mapper;
  private final JsonColumnConverter json;

  TicketRepositoryAdapter(
      SpringDataTicketRepository tickets,
      SpringDataTicketExtractionRepository extractions,
      TicketPersistenceMapper mapper,
      JsonColumnConverter json) {
    this.tickets = tickets;
    this.extractions = extractions;
    this.mapper = mapper;
    this.json = json;
  }

  @Override
  public void add(Ticket ticket, TicketExtraction extraction, Instant now) {
    try {
      tickets.saveAndFlush(mapper.toEntity(ticket));
    } catch (DataIntegrityViolationException e) {
      // Two accounts importing the same ticket at once: the unique hash lets only one through.
      if (isDuplicateEntryCode(e)) {
        throw new ConflictException(
            "TICKET_ALREADY_IMPORTED", "This ticket is already in use by another account.");
      }
      throw e;
    }
    extractions.save(mapper.toExtractionEntity(ticket.getId(), extraction, now, json));
  }

  @Override
  public Optional<Ticket> findByEntryCodeHash(EntryCodeHash hash) {
    return tickets.findByEntryCodeHash(hash.value()).flatMap(this::withOwner);
  }

  @Override
  public List<Ticket> findByOwner(UUID ownerId) {
    return tickets.findByOwner(ownerId).stream()
        .map(entity -> mapper.toDomain(entity, ownerId))
        .toList();
  }

  @Override
  public Optional<Ticket> findById(UUID id) {
    return tickets.findById(id).flatMap(this::withOwner);
  }

  private Optional<Ticket> withOwner(TicketEntity entity) {
    return tickets.findOwnerOf(entity.getId()).map(owner -> mapper.toDomain(entity, owner));
  }

  /** Whether the violation is the unique entry code hash, not another constraint. */
  private static boolean isDuplicateEntryCode(DataIntegrityViolationException e) {
    for (Throwable cause = e; cause != null; cause = cause.getCause()) {
      String message = cause.getMessage();
      if (message != null && message.contains(ENTRY_CODE_HASH_CONSTRAINT)) {
        return true;
      }
    }
    return false;
  }
}
