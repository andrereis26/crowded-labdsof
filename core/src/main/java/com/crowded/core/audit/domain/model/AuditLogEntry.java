package com.crowded.core.audit.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Who did what and when (SR-006). Written once, never changed.
 *
 * @param details the facts of the action, which vary by action
 */
public record AuditLogEntry(
    UUID id,
    UUID actorId,
    UUID eventId,
    String action,
    String entityType,
    UUID entityId,
    Map<String, Object> details,
    Instant occurredAt) {

  public AuditLogEntry {
    details = Collections.unmodifiableMap(new LinkedHashMap<>(details));
  }
}
