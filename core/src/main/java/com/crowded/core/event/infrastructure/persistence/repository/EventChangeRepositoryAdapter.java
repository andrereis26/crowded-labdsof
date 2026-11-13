package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.infrastructure.persistence.mapper.EventChangePersistenceMapper;
import com.crowded.core.shared.infrastructure.persistence.JsonColumnConverter;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;

/** {@link EventChangeRepository} on PostgreSQL. */
@Repository
class EventChangeRepositoryAdapter implements EventChangeRepository {

  private final SpringDataEventChangeRepository changes;
  private final EventChangePersistenceMapper mapper;
  private final JsonColumnConverter json;

  EventChangeRepositoryAdapter(
      SpringDataEventChangeRepository changes,
      EventChangePersistenceMapper mapper,
      JsonColumnConverter json) {
    this.changes = changes;
    this.mapper = mapper;
    this.json = json;
  }

  @Override
  public void save(EventChange change) {
    changes.save(mapper.toEntity(change));
  }

  @Override
  public Optional<EventChange> findLatest(UUID eventId, ChangeType type) {
    return changes
        .findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(eventId, type)
        .map(entity -> mapper.toDomain(entity, json));
  }

  @Override
  public Optional<EventChange> findLatest(UUID eventId) {
    return changes
        .findFirstByEventIdOrderByProvenanceLastUpdatedAtDescIdDesc(eventId)
        .map(entity -> mapper.toDomain(entity, json));
  }

  @Override
  public List<EventChange> findByEvent(UUID eventId) {
    return changes.findByEventIdOrderByProvenanceLastUpdatedAtDescIdDesc(eventId).stream()
        .map(entity -> mapper.toDomain(entity, json))
        .toList();
  }
}
