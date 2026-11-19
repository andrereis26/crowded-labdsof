package com.crowded.core.connection.application.dto;

import com.crowded.core.connection.domain.model.ConnectionType;
import java.util.UUID;

/**
 * A participant's connection with its event.
 *
 * @param ticket null for a connection made with an access code
 */
public record MyConnection(
    UUID connectionId, ConnectionType type, MyEvent event, TicketSummary ticket) {}
