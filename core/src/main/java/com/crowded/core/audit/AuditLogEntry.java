package com.crowded.core.audit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.ColumnTransformer;

/** Who did what and when (SR-006). Written once, never changed. */
@Entity
@Table(name = "audit_log_entry")
public class AuditLogEntry {

  @Id private UUID id;

  @Column(name = "actor_id")
  private UUID actorId;

  @Column(name = "event_id")
  private UUID eventId;

  @Column(nullable = false)
  private String action;

  @Column(name = "entity_type", nullable = false)
  private String entityType;

  @Column(name = "entity_id", nullable = false)
  private UUID entityId;

  @Column(columnDefinition = "jsonb", nullable = false)
  @ColumnTransformer(write = "?::jsonb")
  private String details;

  @Column(name = "occurred_at", nullable = false)
  private Instant occurredAt;

  protected AuditLogEntry() {}

  AuditLogEntry(
      UUID actorId,
      UUID eventId,
      String action,
      String entityType,
      UUID entityId,
      String details,
      Instant occurredAt) {
    this.id = UUID.randomUUID();
    this.actorId = actorId;
    this.eventId = eventId;
    this.action = action;
    this.entityType = entityType;
    this.entityId = entityId;
    this.details = details;
    this.occurredAt = occurredAt;
  }

  public UUID getActorId() {
    return actorId;
  }

  public String getAction() {
    return action;
  }

  public UUID getEntityId() {
    return entityId;
  }

  public String getDetails() {
    return details;
  }
}
