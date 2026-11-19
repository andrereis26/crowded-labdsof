package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.infrastructure.persistence.entity.EventChangeEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code event_change}. */
interface SpringDataEventChangeRepository extends JpaRepository<EventChangeEntity, UUID> {

  Optional<EventChangeEntity> findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(
      UUID eventId, ChangeType type);

  Optional<EventChangeEntity> findFirstByEventIdOrderByProvenanceLastUpdatedAtDescIdDesc(
      UUID eventId);

  List<EventChangeEntity> findByEventIdOrderByProvenanceLastUpdatedAtDescIdDesc(UUID eventId);

  /** The changes of these events, the newest first, so the first of each event is its latest. */
  List<EventChangeEntity> findByEventIdInOrderByProvenanceLastUpdatedAtDescIdDesc(
      Collection<UUID> eventIds);
}
