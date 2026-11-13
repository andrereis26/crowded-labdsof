package com.crowded.core.ticket.infrastructure.persistence.entity;

import java.util.UUID;

/** A pending ticket import as it is stored in Redis, as JSON. */
public record TicketImportEntity(
    UUID id,
    UUID ownerId,
    String encryptedEntryCode,
    String extractedEventCode,
    String gate,
    String sector,
    String seat) {}
