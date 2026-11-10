package com.crowded.core.audit.infrastructure.persistence.repository;

import com.crowded.core.audit.domain.model.AuditLogEntry;
import com.crowded.core.audit.domain.repository.AuditLogRepository;
import com.crowded.core.audit.infrastructure.persistence.mapper.AuditLogEntryPersistenceMapper;
import org.springframework.stereotype.Repository;

/** {@link AuditLogRepository} on PostgreSQL. */
@Repository
class AuditLogRepositoryAdapter implements AuditLogRepository {

  private final SpringDataAuditLogRepository entries;
  private final AuditLogEntryPersistenceMapper mapper;

  AuditLogRepositoryAdapter(
      SpringDataAuditLogRepository entries, AuditLogEntryPersistenceMapper mapper) {
    this.entries = entries;
    this.mapper = mapper;
  }

  @Override
  public void save(AuditLogEntry entry) {
    entries.save(mapper.toEntity(entry));
  }
}
