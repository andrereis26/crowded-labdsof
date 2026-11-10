package com.crowded.core.outbox;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes outgoing messages in the caller's transaction, so a message exists exactly when the change
 * behind it was committed ({@link OutboxRelay} publishes it).
 */
@Component
public class Outbox {

  private final OutboxRepository messages;
  private final JsonMapper json;
  private final Clock clock;

  Outbox(OutboxRepository messages, JsonMapper json, Clock clock) {
    this.messages = messages;
    this.json = json;
    this.clock = clock;
  }

  /** Asks Notifications to notify the participants connected to {@code eventId}. */
  @Transactional(propagation = Propagation.MANDATORY)
  public NotificationRequested requestNotification(String type, UUID eventId, UUID causeId) {
    NotificationRequested message =
        new NotificationRequested(UUID.randomUUID(), type, eventId, causeId, List.of());
    messages.save(
        new OutboxMessage(
            message.messageId(),
            NotificationRequested.ROUTING_KEY,
            json.writeValueAsString(message),
            clock.instant()));
    return message;
  }
}
