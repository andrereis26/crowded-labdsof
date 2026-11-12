package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.EventEntity;
import jakarta.persistence.LockModeType;
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

  /** Rows of {organization id, number of events}. */
  @Query("select e.organizationId, count(e) from EventEntity e group by e.organizationId")
  List<Object[]> countGroupedByOrganization();
}
