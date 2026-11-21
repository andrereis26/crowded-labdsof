package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.QueueTimeEntity;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code queue_time}. */
interface SpringDataQueueTimeRepository extends JpaRepository<QueueTimeEntity, UUID> {

  List<QueueTimeEntity> findByEventIdIn(Collection<UUID> eventIds);
}
