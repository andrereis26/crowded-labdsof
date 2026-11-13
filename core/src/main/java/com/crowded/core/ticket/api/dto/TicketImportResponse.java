package com.crowded.core.ticket.api.dto;

import com.crowded.core.ticket.application.dto.TicketImportStatus;
import java.util.UUID;

/**
 * Answer of {@code POST /tickets} (API Outline): the status, the pending import that the typed
 * event code completes, and the ticket's details.
 */
public record TicketImportResponse(
    TicketImportStatus status, UUID importId, TicketDetailsResponse ticket) {}
