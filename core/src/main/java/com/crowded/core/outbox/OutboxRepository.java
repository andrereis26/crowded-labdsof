package com.crowded.core.outbox;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.NativeQuery;

/** The outbox. */
interface OutboxRepository extends JpaRepository<OutboxMessage, UUID> {

  /**
   * The oldest waiting messages, locked until the transaction ends. Rows another relay holds are
   * skipped, so two Core replicas never publish the same row at the same time.
   */
  @NativeQuery("SELECT * FROM outbox ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED")
  List<OutboxMessage> lockOldest(int limit);
}
