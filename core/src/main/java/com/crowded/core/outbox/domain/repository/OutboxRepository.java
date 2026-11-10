package com.crowded.core.outbox.domain.repository;

import com.crowded.core.outbox.domain.model.NotificationRequested;
import com.crowded.core.outbox.domain.model.OutboxMessage;
import java.util.List;
import java.util.UUID;

/** The outbox: messages wait here, from the change's commit until RabbitMQ confirms them. */
public interface OutboxRepository {

  /** Adds the message, in the caller's transaction. */
  void add(NotificationRequested message);

  /**
   * The oldest waiting messages, locked until the transaction ends. Messages another relay holds
   * are skipped, so two Core replicas never publish the same one at the same time.
   */
  List<OutboxMessage> lockOldest(int limit);

  /** Removes a published message. */
  void remove(UUID id);
}
