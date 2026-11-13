package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.QueuePointEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code queue_point}. */
interface SpringDataQueuePointRepository extends JpaRepository<QueuePointEntity, UUID> {

  List<QueuePointEntity> findByEventIdInOrderByPosition(Collection<UUID> eventIds);
}
