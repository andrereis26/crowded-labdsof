package com.crowded.core.audit.domain.repository;

import com.crowded.core.audit.domain.model.AuditLogEntry;

/** The audit log. */
public interface AuditLogRepository {

  void save(AuditLogEntry entry);
}
