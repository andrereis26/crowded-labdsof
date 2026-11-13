package com.crowded.core.ticket.application.dto;

import com.crowded.core.ticket.domain.model.TicketDetails;
import java.util.UUID;

/** The result of importing a ticket file, with the fields the API Outline gives it. */
public record TicketImportResult(TicketImportStatus status, UUID importId, TicketDetails ticket) {}
