package com.crowded.core.connection.infrastructure.persistence.repository;

import com.crowded.core.connection.infrastructure.persistence.entity.EventConnectionEntity;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@code event_connection} and the tables of each kind of connection. */
interface SpringDataEventConnectionRepository extends JpaRepository<EventConnectionEntity, UUID> {

  boolean existsByUserIdAndEventId(UUID userId, UUID eventId);

  List<EventConnectionEntity> findByUserId(UUID userId);

  Optional<EventConnectionEntity> findFirstByUserIdAndEventIdOrderByConnectedAt(
      UUID userId, UUID eventId);

  /** Rows of {event id, number of connections}, for the events in {@code eventIds}. */
  @Query(
      "select c.eventId, count(c) from EventConnectionEntity c"
          + " where c.eventId in :eventIds group by c.eventId")
  List<Object[]> countGroupedByEvent(Collection<UUID> eventIds);

  /**
   * Takes the transaction-scoped advisory lock ({@code space}, {@code key}); returns 1. The pair of
   * keys keeps it apart from the single-key locks other modules take.
   */
  @Query(
      value =
          "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(:space, hashtext(:key))) AS locked",
      nativeQuery = true)
  long lockUntilCommit(int space, String key);
}
