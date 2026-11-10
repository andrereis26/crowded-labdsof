package com.crowded.core.outbox.infrastructure.persistence.repository;

import com.crowded.core.outbox.domain.model.NotificationRequested;
import com.crowded.core.outbox.domain.model.OutboxMessage;
import com.crowded.core.outbox.domain.repository.OutboxRepository;
import com.crowded.core.outbox.infrastructure.persistence.mapper.OutboxMessagePersistenceMapper;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/** {@link OutboxRepository} on PostgreSQL; messages are stored as their JSON. */
@Repository
class OutboxRepositoryAdapter implements OutboxRepository {

  private final SpringDataOutboxRepository messages;
  private final OutboxMessagePersistenceMapper mapper;
  private final JsonMapper json;
  private final Clock clock;

  OutboxRepositoryAdapter(
      SpringDataOutboxRepository messages,
      OutboxMessagePersistenceMapper mapper,
      JsonMapper json,
      Clock clock) {
    this.messages = messages;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
  }

  @Override
  public void add(NotificationRequested message) {
    messages.save(
        mapper.toEntity(
            new OutboxMessage(
                message.messageId(),
                NotificationRequested.ROUTING_KEY,
                json.writeValueAsString(message),
                clock.instant())));
  }

  @Override
  public List<OutboxMessage> lockOldest(int limit) {
    return messages.lockOldest(limit).stream().map(mapper::toDomain).toList();
  }

  @Override
  public void remove(UUID id) {
    messages.deleteById(id);
  }
}
