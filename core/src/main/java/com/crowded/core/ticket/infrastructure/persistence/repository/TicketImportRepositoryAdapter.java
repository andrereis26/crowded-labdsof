package com.crowded.core.ticket.infrastructure.persistence.repository;

import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.ticket.domain.model.TicketImport;
import com.crowded.core.ticket.domain.repository.TicketImportRepository;
import com.crowded.core.ticket.infrastructure.persistence.entity.TicketImportEntity;
import com.crowded.core.ticket.infrastructure.persistence.mapper.TicketImportPersistenceMapper;
import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link TicketImportRepository} in Redis (ADR 0004): {@code ticket-import:<id>} holds the import
 * as JSON and expires with it, entry code included. The entry code is already encrypted.
 */
@Repository
class TicketImportRepositoryAdapter implements TicketImportRepository {

  static final String PREFIX = "ticket-import:";

  static final Duration TIME_TO_LIVE = Duration.ofMinutes(TicketImport.TIME_TO_LIVE_MINUTES);

  private final StringRedisTemplate redis;
  private final JsonMapper json;
  private final TicketImportPersistenceMapper mapper;

  TicketImportRepositoryAdapter(
      StringRedisTemplate redis, JsonMapper json, TicketImportPersistenceMapper mapper) {
    this.redis = redis;
    this.json = json;
    this.mapper = mapper;
  }

  @Override
  public void save(TicketImport ticketImport) {
    try {
      redis
          .opsForValue()
          .set(
              PREFIX + ticketImport.getId(),
              json.writeValueAsString(mapper.toEntity(ticketImport)),
              TIME_TO_LIVE);
    } catch (DataAccessException e) {
      throw unavailable();
    }
  }

  @Override
  public Optional<TicketImport> findById(UUID importId) {
    try {
      return Optional.ofNullable(redis.opsForValue().get(PREFIX + importId))
          .map(value -> mapper.toDomain(json.readValue(value, TicketImportEntity.class)));
    } catch (DataAccessException e) {
      throw unavailable();
    }
  }

  private static UnavailableException unavailable() {
    return new UnavailableException(
        "TICKET_IMPORT_UNAVAILABLE", "This is not available right now. Try again soon.");
  }
}
