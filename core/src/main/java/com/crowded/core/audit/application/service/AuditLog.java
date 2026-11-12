package com.crowded.core.audit.application.service;

import com.crowded.core.audit.domain.model.AuditLogEntry;
import com.crowded.core.audit.domain.repository.AuditLogRepository;
import java.time.Clock;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Records actions in the audit log (SR-006), in the caller's transaction. */
@Service
public class AuditLog {

  private final AuditLogRepository entries;
  private final Clock clock;

  public AuditLog(AuditLogRepository entries, Clock clock) {
    this.entries = entries;
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
      Map<String, Object> details) {
    entries.save(
        AuditLogEntry.record(
            actorId, eventId, action, entityType, entityId, details, clock.instant()));
  }
}
