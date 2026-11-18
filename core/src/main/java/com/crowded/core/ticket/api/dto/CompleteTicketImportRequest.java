package com.crowded.core.ticket.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Body of {@code POST /ticket-imports/{importId}/event-code}: the code printed on the ticket. */
public record CompleteTicketImportRequest(@NotBlank @Size(max = 64) String eventCode) {}
