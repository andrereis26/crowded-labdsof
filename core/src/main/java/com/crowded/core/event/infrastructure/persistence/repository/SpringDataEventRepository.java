package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.infrastructure.persistence.entity.EventEntity;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

/** Spring Data access to {@code event}. */
interface SpringDataEventRepository extends JpaRepository<EventEntity, UUID> {

  @Lock(LockModeType.PESSIMISTIC_WRITE)
  Optional<EventEntity> findForUpdateById(UUID id);

  List<EventEntity> findByOrganizationIdOrderByStartDateTimeDesc(UUID organizationId);

  /**
   * Whether an event other than {@code eventId}, not in one of the {@code closed} statuses, that
   * ends after {@code now} uses {@code code}.
   */
  @Query(
      "select count(e) > 0 from EventEntity e"
          + " where (upper(e.ticketEventCode) = :code or upper(e.accessCode) = :code)"
          + " and e.id <> :eventId and e.endDateTime > :now and e.status not in :closed")
  boolean existsUpcomingWithCode(
      String code, UUID eventId, Instant now, Collection<EventStatus> closed);

  /**
   * The public event, not in one of the {@code closed} statuses, that ends after {@code now} and
   * has {@code code} as its access code. A ticketed event has none.
   */
  @Query(
      "select e from EventEntity e"
          + " where upper(e.accessCode) = :code and e.endDateTime > :now and e.status not in :closed")
  Optional<EventEntity> findUpcomingByAccessCode(
      String code, Instant now, Collection<EventStatus> closed);

  /** Takes the transaction-scoped advisory lock {@code key}; returns 1. */
  @Query(
      value = "SELECT count(*) FROM (SELECT pg_advisory_xact_lock(:key)) AS locked",
      nativeQuery = true)
  long lockUntilCommit(long key);

  /** Rows of {organization id, number of events}. */
  @Query("select e.organizationId, count(e) from EventEntity e group by e.organizationId")
  List<Object[]> countGroupedByOrganization();
}
