package com.crowded.core.ticket.application.dto;

import java.util.UUID;

/**
 * The event code a participant typed to complete their pending import.
 *
 * @param ownerId the signed-in participant, never read from the request body
 */
public record CompleteTicketImportCommand(UUID ownerId, UUID importId, String eventCode) {}
