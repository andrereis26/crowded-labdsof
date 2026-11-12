package com.crowded.core.connection.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** Row of {@code event_connection}, read only so far: no endpoint creates connections yet. */
@Entity
@Table(name = "event_connection")
public class EventConnectionEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(name = "connected_at", nullable = false)
  private Instant connectedAt;

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public Instant getConnectedAt() {
    return connectedAt;
  }
}
