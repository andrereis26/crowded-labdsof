package com.crowded.core.ticket.api.dto;

/** The ticket's gate, sector and seat, each {@code null} when it was not found. */
public record TicketDetailsResponse(String gate, String sector, String seat) {}
