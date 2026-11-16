package com.crowded.core.connection.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Inheritance;
import jakarta.persistence.InheritanceType;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Row of {@code event_connection}. Each kind of connection adds its own table with the same id: a
 * row of this class alone is a ticket, until tickets get their entity.
 */
@Entity
@Table(name = "event_connection")
@Inheritance(strategy = InheritanceType.JOINED)
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

  public void setId(UUID id) {
    this.id = id;
  }

  public UUID getUserId() {
    return userId;
  }

  public void setUserId(UUID userId) {
    this.userId = userId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public void setEventId(UUID eventId) {
    this.eventId = eventId;
  }

  public Instant getConnectedAt() {
    return connectedAt;
  }

  public void setConnectedAt(Instant connectedAt) {
    this.connectedAt = connectedAt;
  }
}
