package com.crowded.core.audit;

import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/** Records actions in the audit log (SR-006), in the caller's transaction. */
@Component
public class AuditLog {

  private final AuditLogEntryRepository entries;
  private final JsonMapper json;
  private final Clock clock;

  AuditLog(AuditLogEntryRepository entries, JsonMapper json, Clock clock) {
    this.entries = entries;
    this.json = json;
    this.clock = clock;
  }

  /** Records that {@code actorId} did {@code action} to an entity of an event. */
  @Transactional(propagation = Propagation.MANDATORY)
  public void record(
      UUID actorId,
      UUID eventId,
      String action,
      String entityType,
      UUID entityId,
      Map<String, ?> details) {
    entries.save(
        new AuditLogEntry(
            actorId,
            eventId,
            action,
            entityType,
            entityId,
            json.writeValueAsString(details),
            clock.instant()));
  }
}
