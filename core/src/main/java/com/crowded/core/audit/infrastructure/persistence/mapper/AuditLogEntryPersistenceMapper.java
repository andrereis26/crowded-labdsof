package com.crowded.core.audit.infrastructure.persistence.mapper;

import com.crowded.core.audit.domain.model.AuditLogEntry;
import com.crowded.core.audit.infrastructure.persistence.entity.AuditLogEntryEntity;
import com.crowded.core.shared.infrastructure.persistence.JsonColumnConverter;
import org.mapstruct.Mapper;

/** Maps audit log entries to {@code audit_log_entry} rows; details become JSON. */
@Mapper(uses = JsonColumnConverter.class)
public interface AuditLogEntryPersistenceMapper {

  AuditLogEntryEntity toEntity(AuditLogEntry entry);
}
