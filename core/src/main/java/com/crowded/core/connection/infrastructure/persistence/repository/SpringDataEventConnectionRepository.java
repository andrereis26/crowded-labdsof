package com.crowded.core.connection.infrastructure.persistence.repository;

import com.crowded.core.connection.infrastructure.persistence.entity.EventConnectionEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@code event_connection}. */
interface SpringDataEventConnectionRepository extends JpaRepository<EventConnectionEntity, UUID> {

  boolean existsByUserIdAndEventId(UUID userId, UUID eventId);

  /** Rows of {event id, number of connections}, for the events in {@code eventIds}. */
  @Query(
      "select c.eventId, count(c) from EventConnectionEntity c"
          + " where c.eventId in :eventIds group by c.eventId")
  List<Object[]> countGroupedByEvent(Collection<UUID> eventIds);
}
