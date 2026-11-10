package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.infrastructure.persistence.mapper.EventChangePersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link EventChangeRepository} on PostgreSQL. */
@Repository
class EventChangeRepositoryAdapter implements EventChangeRepository {

  private final SpringDataEventChangeRepository changes;
  private final EventChangePersistenceMapper mapper;

  EventChangeRepositoryAdapter(
      SpringDataEventChangeRepository changes, EventChangePersistenceMapper mapper) {
    this.changes = changes;
    this.mapper = mapper;
  }

  @Override
  public void save(EventChange change) {
    changes.save(mapper.toEntity(change));
  }

  @Override
  public Optional<EventChange> findLatest(UUID eventId, ChangeType type) {
    return changes
        .findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(eventId, type)
        .map(mapper::toDomain);
  }
}
