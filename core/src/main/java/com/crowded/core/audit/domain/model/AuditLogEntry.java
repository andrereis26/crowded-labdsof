package com.crowded.core.audit.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/** Who did what and when. Written once, never changed. */
public final class AuditLogEntry {

  private final UUID id;
  private final UUID actorId;
  private final UUID eventId;
  private final String action;
  private final String entityType;
  private final UUID entityId;
  private final Map<String, Object> details;
  private final Instant occurredAt;

  private AuditLogEntry(
      UUID id,
      UUID actorId,
      UUID eventId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> details,
      Instant occurredAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.actorId = actorId;
    this.eventId = eventId;
    this.action = Objects.requireNonNull(action, "action");
    this.entityType = Objects.requireNonNull(entityType, "entityType");
    this.entityId = entityId;
    this.details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
    this.occurredAt = Objects.requireNonNull(occurredAt, "occurredAt");
  }

  /**
   * The entry for {@code actorId} doing {@code action} at {@code now} to an entity, of an event or
   * of none ({@code eventId} null).
   *
   * @param details the facts of the action, which vary by action
   */
  public static AuditLogEntry record(
      UUID actorId,
      UUID eventId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, Object> details,
      Instant now) {
    return new AuditLogEntry(
        UUID.randomUUID(), actorId, eventId, action, entityType, entityId, details, now);
  }

  public UUID getId() {
    return id;
  }

  public UUID getActorId() {
    return actorId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getAction() {
    return action;
  }

  public String getEntityType() {
    return entityType;
  }

  public UUID getEntityId() {
    return entityId;
  }

  /** The facts of the action, which vary by action; read-only. */
  public Map<String, Object> getDetails() {
    return details;
  }

  public Instant getOccurredAt() {
    return occurredAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof AuditLogEntry entry && id.equals(entry.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "AuditLogEntry[" + id + ", " + action + "]";
  }
}
