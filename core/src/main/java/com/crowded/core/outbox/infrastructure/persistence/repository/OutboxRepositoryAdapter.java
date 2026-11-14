package com.crowded.core.outbox.infrastructure.persistence.repository;

import com.crowded.core.outbox.domain.model.NotificationRequested;
import com.crowded.core.outbox.domain.model.OutboxMessage;
import com.crowded.core.outbox.domain.repository.OutboxRepository;
import com.crowded.core.outbox.infrastructure.persistence.mapper.OutboxMessagePersistenceMapper;
import io.micrometer.tracing.TraceContext;
import io.micrometer.tracing.Tracer;
import io.micrometer.tracing.propagation.Propagator;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.json.JsonMapper;

/**
 * {@link OutboxRepository} on PostgreSQL; messages are stored as their JSON, with the trace of the
 * request that added them.
 */
@Repository
class OutboxRepositoryAdapter implements OutboxRepository {

  private final SpringDataOutboxRepository messages;
  private final OutboxMessagePersistenceMapper mapper;
  private final JsonMapper json;
  private final Clock clock;
  private final Tracer tracer;
  private final Propagator propagator;

  OutboxRepositoryAdapter(
      SpringDataOutboxRepository messages,
      OutboxMessagePersistenceMapper mapper,
      JsonMapper json,
      Clock clock,
      Tracer tracer,
      Propagator propagator) {
    this.messages = messages;
    this.mapper = mapper;
    this.json = json;
    this.clock = clock;
    this.tracer = tracer;
    this.propagator = propagator;
  }

  @Override
  public void add(NotificationRequested message) {
    messages.save(
        mapper.toEntity(
            OutboxMessage.of(
                message, json.writeValueAsString(message), clock.instant(), traceParent())));
  }

  @Override
  public List<OutboxMessage> lockOldest(int limit) {
    return messages.lockOldest(limit).stream().map(mapper::toDomain).toList();
  }

  @Override
  public void remove(UUID id) {
    messages.deleteById(id);
  }

  /** The current trace as a W3C traceparent, or {@code null} outside a trace. */
  private String traceParent() {
    TraceContext context = tracer.currentTraceContext().context();
    if (context == null) {
      return null;
    }
    Map<String, String> headers = new HashMap<>();
    propagator.inject(context, headers, Map::put);
    return headers.get("traceparent");
  }
}
