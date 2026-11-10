package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.infrastructure.persistence.entity.EventChangeEntity;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code event_change}. */
interface SpringDataEventChangeRepository extends JpaRepository<EventChangeEntity, UUID> {

  Optional<EventChangeEntity> findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(
      UUID eventId, ChangeType type);
}
