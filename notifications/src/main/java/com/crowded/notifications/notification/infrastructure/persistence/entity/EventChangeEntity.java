package com.crowded.notifications.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/**
 * Row of Core's {@code event_change}, read only (ADR 0002). Its changed values are read with {@code
 * OfficialChangeRow}.
 */
@Entity
@Immutable
@Table(name = "event_change")
public class EventChangeEntity {

  @Id private UUID id;

  @Column(name = "event_id", nullable = false)
  private UUID eventId;

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }
}
