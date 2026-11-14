package com.crowded.core.outbox.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** A message committed with a change and not yet confirmed by RabbitMQ. */
public final class OutboxMessage {

  private final UUID id;
  private final String routingKey;
  private final String payload;
  private final Instant createdAt;
  private final String traceParent;

  private OutboxMessage(
      UUID id, String routingKey, String payload, Instant createdAt, String traceParent) {
    this.id = Objects.requireNonNull(id, "id");
    this.routingKey = Objects.requireNonNull(routingKey, "routingKey");
    this.payload = Objects.requireNonNull(payload, "payload");
    this.createdAt = Objects.requireNonNull(createdAt, "createdAt");
    this.traceParent = traceParent;
  }

  /**
   * The outbox message that carries {@code message}.
   *
   * @param payload {@code message} as JSON
   * @param traceParent the W3C traceparent of the request that asked for it; {@code null} outside a
   *     trace
   */
  public static OutboxMessage of(
      NotificationRequested message, String payload, Instant now, String traceParent) {
    return new OutboxMessage(
        message.messageId(), NotificationRequested.ROUTING_KEY, payload, now, traceParent);
  }

  /** A stored message, as it was saved. */
  public static OutboxMessage restore(
      UUID id, String routingKey, String payload, Instant createdAt, String traceParent) {
    return new OutboxMessage(id, routingKey, payload, createdAt, traceParent);
  }

  /** The message id, the same on every publication. */
  public UUID getId() {
    return id;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  /** The message as JSON. */
  public String getPayload() {
    return payload;
  }

  /** When the change behind the message was committed. */
  public Instant getCreatedAt() {
    return createdAt;
  }

  /**
   * The W3C traceparent of the request that asked for the message, so its consumer logs under the
   * same trace id; {@code null} when there was none.
   */
  public String getTraceParent() {
    return traceParent;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof OutboxMessage message && id.equals(message.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "OutboxMessage[" + id + ", " + routingKey + "]";
  }
}
