package com.crowded.core.outbox.domain.model;

import java.time.Instant;
import java.util.UUID;

/**
 * A message committed with a change and not yet confirmed by RabbitMQ.
 *
 * @param id the message id, the same on every publication
 * @param payload the message as JSON
 */
public record OutboxMessage(UUID id, String routingKey, String payload, Instant createdAt) {}
