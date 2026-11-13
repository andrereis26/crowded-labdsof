package com.crowded.core.connection.infrastructure.persistence.repository;

import com.crowded.core.connection.domain.repository.ConnectionRepository;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Repository;

/** {@link ConnectionRepository} on PostgreSQL. */
@Repository
class ConnectionRepositoryAdapter implements ConnectionRepository {

  private final SpringDataEventConnectionRepository connections;

  ConnectionRepositoryAdapter(SpringDataEventConnectionRepository connections) {
    this.connections = connections;
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
}
