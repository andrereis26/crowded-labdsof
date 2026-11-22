package com.crowded.notifications.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Row of Core's {@code alert}, read only (ADR 0002). */
@Entity
@Immutable
@Table(name = "alert")
public class AlertEntity {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  @Column(columnDefinition = "alert_type", nullable = false)
  private String type;

  @Column(nullable = false)
  private String message;

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getType() {
    return type;
  }

  public String getMessage() {
    return message;
  }
}
