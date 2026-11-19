package com.crowded.core.connection.api.dto;

/** The participant's place on their ticket; each may be null. */
public record MyTicketResponse(String gate, String sector, String seat, boolean hasEntryCode) {}
