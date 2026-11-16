package com.crowded.core.connection.api.dto;

import java.util.UUID;

/** Answer of {@code POST /connections} (API Outline): the connection and its event. */
public record ConnectionResponse(UUID connectionId, ConnectedEventResponse event) {}
