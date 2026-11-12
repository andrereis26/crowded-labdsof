package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.domain.model.Connection;
import com.crowded.notifications.notification.domain.model.NotificationType;
import com.crowded.notifications.notification.domain.repository.ConnectionRepository;
import com.crowded.notifications.notification.infrastructure.persistence.mapper.ConnectionPersistenceMapper;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link ConnectionRepository} on Core's tables in PostgreSQL. */
@Repository
class ConnectionRepositoryAdapter implements ConnectionRepository {

  private final SpringDataConnectionRepository connections;
  private final ConnectionPersistenceMapper mapper;

  ConnectionRepositoryAdapter(
      SpringDataConnectionRepository connections, ConnectionPersistenceMapper mapper) {
    this.connections = connections;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public List<Connection> findByEvent(UUID eventId, NotificationType type) {
    return mapper.toDomain(connections.findByEvent(eventId, type.name()));
  }
}
