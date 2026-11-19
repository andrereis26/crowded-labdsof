package com.crowded.core.connection.application.dto;

/**
 * What a participant's ticket says about their place; each may be null. The entry code itself is
 * never part of the list.
 */
public record TicketSummary(String gate, String sector, String seat, boolean hasEntryCode) {}
