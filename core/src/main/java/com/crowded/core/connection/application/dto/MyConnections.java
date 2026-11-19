package com.crowded.core.connection.application.dto;

import java.time.Instant;
import java.util.List;

/**
 * The participant's connections in the order they see them.
 *
 * @param syncedAt when Core read the list, so the app can show how old it is
 */
public record MyConnections(List<MyConnection> connections, Instant syncedAt) {

  public MyConnections {
    connections = List.copyOf(connections);
  }
}
