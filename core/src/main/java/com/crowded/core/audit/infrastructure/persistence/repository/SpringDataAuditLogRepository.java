package com.crowded.core.audit.infrastructure.persistence.repository;

import com.crowded.core.audit.infrastructure.persistence.entity.AuditLogEntryEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code audit_log_entry}. */
interface SpringDataAuditLogRepository extends JpaRepository<AuditLogEntryEntity, UUID> {}
