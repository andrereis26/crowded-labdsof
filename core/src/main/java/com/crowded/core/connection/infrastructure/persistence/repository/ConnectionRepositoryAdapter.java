package com.crowded.core.connection.infrastructure.persistence.repository;

import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.connection.infrastructure.persistence.mapper.ConnectionPersistenceMapper;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** {@link ConnectionRepository} on PostgreSQL. */
@Repository
class ConnectionRepositoryAdapter implements ConnectionRepository {

  /** First key of the advisory locks on a participant's connections. */
  static final int PARTICIPANT_CONNECTIONS_LOCK = 0x434f4e4e;

  private final SpringDataEventConnectionRepository connections;
  private final ConnectionPersistenceMapper mapper;

  ConnectionRepositoryAdapter(
      SpringDataEventConnectionRepository connections, ConnectionPersistenceMapper mapper) {
    this.connections = connections;
    this.mapper = mapper;
  }

  @Override
  public boolean isConnected(UUID userId, UUID eventId) {
    return connections.existsByUserIdAndEventId(userId, eventId);
  }

  @Override
  public Map<UUID, Long> countByEvent(Collection<UUID> eventIds) {
    if (eventIds.isEmpty()) {
      return Map.of();
    }
    return connections.countGroupedByEvent(eventIds).stream()
        .collect(Collectors.toMap(row -> (UUID) row[0], row -> (Long) row[1]));
  }

  @Override
  public void lockConnectionsOf(UUID userId) {
    connections.lockUntilCommit(PARTICIPANT_CONNECTIONS_LOCK, userId.toString());
  }

  @Override
  public Optional<EventConnection> findByUserAndEvent(UUID userId, UUID eventId) {
    return connections
        .findFirstByUserIdAndEventIdOrderByConnectedAt(userId, eventId)
        .map(mapper::toDomain);
  }

  @Override
  public void add(EventConnection connection) {
    connections.save(mapper.toAccessCodeEntity(connection));
  }

  @Override
  public void addTicketConnection(EventConnection connection) {
    connections.saveAndFlush(mapper.toTicketConnectionEntity(connection));
  }
}
