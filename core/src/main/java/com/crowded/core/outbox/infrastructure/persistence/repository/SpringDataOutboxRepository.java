package com.crowded.core.outbox.infrastructure.persistence.repository;

import com.crowded.core.outbox.infrastructure.persistence.entity.OutboxMessageEntity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

/** Spring Data access to {@code outbox}. */
interface SpringDataOutboxRepository extends JpaRepository<OutboxMessageEntity, UUID> {

  @NativeQuery("SELECT * FROM outbox ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED")
  List<OutboxMessageEntity> lockOldest(int limit);
}
