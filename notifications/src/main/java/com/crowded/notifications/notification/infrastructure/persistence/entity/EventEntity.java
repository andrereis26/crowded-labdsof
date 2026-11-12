package com.crowded.notifications.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;
import org.hibernate.annotations.Immutable;

/** Row of Core's {@code event}, read only (ADR 0002), for its name. */
@Entity
@Immutable
@Table(name = "event")
public class EventEntity {

  @Id private UUID id;

  @Column(nullable = false)
  private String name;

  public UUID getId() {
    return id;
  }

  public String getName() {
    return name;
  }
}
