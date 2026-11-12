package com.crowded.core.connection.infrastructure.persistence.repository;

import com.crowded.core.connection.infrastructure.persistence.entity.EventConnectionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code event_connection}. */
interface SpringDataEventConnectionRepository extends JpaRepository<EventConnectionEntity, UUID> {

  boolean existsByUserIdAndEventId(UUID userId, UUID eventId);
}
