package com.crowded.core.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** A message waiting to be published to RabbitMQ; its id is the message id. */
@Entity
@Table(name = "outbox")
public class OutboxMessage {

  @Id private UUID id;

  @Column(name = "routing_key", nullable = false)
  private String routingKey;

  @Column(columnDefinition = "jsonb", nullable = false)
  @ColumnTransformer(write = "?::jsonb")
  private String payload;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  protected OutboxMessage() {}

  OutboxMessage(UUID id, String routingKey, String payload, Instant createdAt) {
    this.id = id;
    this.routingKey = routingKey;
    this.payload = payload;
    this.createdAt = createdAt;
  }

  public UUID getId() {
    return id;
  }

  public String getRoutingKey() {
    return routingKey;
  }

  public String getPayload() {
    return payload;
  }
}
