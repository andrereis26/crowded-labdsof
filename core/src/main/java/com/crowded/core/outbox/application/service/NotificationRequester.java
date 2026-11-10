package com.crowded.core.outbox.application.service;

import com.crowded.core.outbox.domain.model.NotificationRequested;
import com.crowded.core.outbox.domain.repository.OutboxRepository;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Asks Notifications to notify participants, through the outbox and in the caller's transaction, so
 * the message exists exactly when the change behind it was committed.
 */
@Service
public class NotificationRequester {

  private final OutboxRepository outbox;

  NotificationRequester(OutboxRepository outbox) {
    this.outbox = outbox;
  }

  /** Asks to notify the participants connected to {@code eventId} of {@code causeId}. */
  @Transactional(propagation = Propagation.MANDATORY)
  public NotificationRequested request(String type, UUID eventId, UUID causeId) {
    NotificationRequested message =
        new NotificationRequested(UUID.randomUUID(), type, eventId, causeId, List.of());
    outbox.add(message);
    return message;
  }
}
