package com.crowded.notifications.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Row of Core's {@code event_connection}, read only (ADR 0002). */
@Entity
@Immutable
@Table(name = "event_connection")
public class EventConnectionEntity {

  @Id private UUID id;

  @Column(name = "user_id", nullable = false)
  private UUID userId;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getEventId() {
    return eventId;
  }
}
