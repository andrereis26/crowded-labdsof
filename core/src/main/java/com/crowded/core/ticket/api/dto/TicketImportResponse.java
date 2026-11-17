package com.crowded.core.ticket.api.dto;

import com.crowded.core.ticket.application.dto.TicketImportStatus;
import java.util.UUID;

/**
 * Answer of {@code POST /tickets} (API Outline): {@code CONNECTED} with the ticket, its connection
 * and the registered event, or {@code NEEDS_EVENT_CODE} with the pending import that the typed
 * event code completes. The ticket's details are as read from the ticket.
 */
public record TicketImportResponse(
    TicketImportStatus status,
    UUID importId,
    UUID ticketId,
    UUID connectionId,
    ImportedEventResponse event,
    TicketDetailsResponse ticket) {}
