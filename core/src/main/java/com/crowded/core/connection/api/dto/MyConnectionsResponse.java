package com.crowded.core.connection.api.dto;

import java.time.Instant;
import java.util.List;

/**
 * Answer of {@code GET /me/connections} (API Outline).
 *
 * @param syncedAt when Core read the list
 */
public record MyConnectionsResponse(List<MyConnectionResponse> connections, Instant syncedAt) {

  public MyConnectionsResponse {
    connections = List.copyOf(connections);
  }
}
