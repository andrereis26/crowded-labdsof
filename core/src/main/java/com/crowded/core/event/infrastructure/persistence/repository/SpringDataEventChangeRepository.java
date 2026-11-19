package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.infrastructure.persistence.entity.EventChangeEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@code event_change}. */
interface SpringDataEventChangeRepository extends JpaRepository<EventChangeEntity, UUID> {

  Optional<EventChangeEntity> findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(
      UUID eventId, ChangeType type);

  Optional<EventChangeEntity> findFirstByEventIdOrderByProvenanceLastUpdatedAtDescIdDesc(
      UUID eventId);

  List<EventChangeEntity> findByEventIdOrderByProvenanceLastUpdatedAtDescIdDesc(UUID eventId);

  /** The latest change of each of these events, one row per event that has any. */
  @Query(
      value =
          "SELECT DISTINCT ON (event_id) * FROM event_change WHERE event_id IN (:eventIds)"
              + " ORDER BY event_id, provenance_last_updated_at DESC, id DESC",
      nativeQuery = true)
  List<EventChangeEntity> findLatestOfEach(Collection<UUID> eventIds);
}
