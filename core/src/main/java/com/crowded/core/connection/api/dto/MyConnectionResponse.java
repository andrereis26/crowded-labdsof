package com.crowded.core.connection.api.dto;

import com.crowded.core.connection.domain.model.ConnectionType;
import java.util.UUID;

/**
 * A connection in {@code GET /me/connections}.
 *
 * @param ticket null for a connection made with an access code
 */
public record MyConnectionResponse(
    UUID connectionId, ConnectionType type, MyEventResponse event, MyTicketResponse ticket) {}
