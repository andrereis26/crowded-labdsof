package com.crowded.notifications.notification.infrastructure.persistence.repository;

import com.crowded.notifications.notification.domain.model.OfficialChange;
import com.crowded.notifications.notification.domain.repository.OfficialChangeRepository;
import com.crowded.notifications.notification.infrastructure.persistence.mapper.OfficialChangePersistenceMapper;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link OfficialChangeRepository} on Core's {@code event_change} in PostgreSQL. */
@Repository
class OfficialChangeRepositoryAdapter implements OfficialChangeRepository {

  private final SpringDataOfficialChangeRepository changes;
  private final OfficialChangePersistenceMapper mapper;

  OfficialChangeRepositoryAdapter(
      SpringDataOfficialChangeRepository changes, OfficialChangePersistenceMapper mapper) {
    this.changes = changes;
    this.mapper = mapper;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<OfficialChange> findById(UUID changeId) {
    return changes.findChangeById(changeId).map(mapper::toDomain);
  }
}
