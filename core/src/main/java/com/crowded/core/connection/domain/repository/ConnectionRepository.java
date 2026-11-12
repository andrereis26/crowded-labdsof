package com.crowded.core.connection.domain.repository;

import java.util.UUID;

/** The participants' connections to events, by a ticket or an access code. */
public interface ConnectionRepository {

  /** Whether {@code userId} is connected to {@code eventId} now; a removed connection is not. */
  boolean isConnected(UUID userId, UUID eventId);
}
