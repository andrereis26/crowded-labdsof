package com.crowded.core.connection.infrastructure.persistence.repository;

import com.crowded.core.connection.domain.repository.ConnectionRepository;
import java.util.UUID;
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
}
