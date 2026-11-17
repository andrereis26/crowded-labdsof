package com.crowded.core.connection.domain.repository;

import com.crowded.core.connection.domain.model.EventConnection;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** The participants' connections to events, by a ticket or an access code. */
public interface ConnectionRepository {

  /** Whether {@code userId} is connected to {@code eventId} now; a removed connection is not. */
  boolean isConnected(UUID userId, UUID eventId);

  /**
   * How many participants are connected to each of {@code eventIds}; an event without any is not in
   * the map.
   */
  Map<UUID, Long> countByEvent(Collection<UUID> eventIds);

  /**
   * Holds {@code userId}'s connections until the transaction ends, so two requests at once, such as
   * a QR code read twice, cannot connect them to the same event twice.
   */
  void lockConnectionsOf(UUID userId);

  /** {@code userId}'s earliest connection to {@code eventId}; empty when there is none. */
  Optional<EventConnection> findByUserAndEvent(UUID userId, UUID eventId);

  /** Stores a new connection to a public event, made with its access code. */
  void add(EventConnection connection);

  /**
   * Stores a new connection made with a ticket. Its row is written at once, so the ticket row that
   * refers to it can follow in the same transaction.
   */
  void addTicketConnection(EventConnection connection);
}
