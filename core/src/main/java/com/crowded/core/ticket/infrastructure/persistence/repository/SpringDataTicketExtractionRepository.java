package com.crowded.core.ticket.infrastructure.persistence.repository;

import com.crowded.core.ticket.infrastructure.persistence.entity.TicketExtractionEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code ticket_extraction}. */
interface SpringDataTicketExtractionRepository
    extends JpaRepository<TicketExtractionEntity, UUID> {}
