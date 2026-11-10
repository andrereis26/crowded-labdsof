package com.crowded.core.event;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Official changes. */
interface EventChangeRepository extends JpaRepository<EventChange, UUID> {

  /** The latest change of this type to the event. */
  Optional<EventChange> findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(
      UUID eventId, ChangeType type);
}
