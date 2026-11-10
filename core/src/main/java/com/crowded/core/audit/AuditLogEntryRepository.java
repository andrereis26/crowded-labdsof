package com.crowded.core.audit;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** The audit log. */
interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, UUID> {}
