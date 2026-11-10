package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.infrastructure.persistence.entity.EventEntity;
import com.crowded.core.event.infrastructure.persistence.mapper.EventPersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link EventRepository} on PostgreSQL. */
@Repository
class EventRepositoryAdapter implements EventRepository {

  private final SpringDataEventRepository events;
  private final EventPersistenceMapper mapper;

  EventRepositoryAdapter(SpringDataEventRepository events, EventPersistenceMapper mapper) {
    this.events = events;
    this.mapper = mapper;
  }

  @Override
  public Optional<Event> findByIdForUpdate(UUID id) {
    return events.findForUpdateById(id).map(mapper::toDomain);
  }

  @Override
  public void save(Event event) {
    // The row read (and locked) by findByIdForUpdate, from the same transaction.
    EventEntity entity =
        events
            .findById(event.getId())
            .orElseThrow(() -> new IllegalStateException("Event " + event.getId() + " is gone"));
    mapper.updateEntity(event, entity);
  }
}
