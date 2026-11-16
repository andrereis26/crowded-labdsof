package com.crowded.core.connection.domain.service;

import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.domain.model.JoinedConnection;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;
import java.util.Optional;

/**
 * Connects a participant to the public event of an access code, once: the event is the one found
 * for the code, never one the client names, and every code that leads to no event gets the same
 * refusal, so it reveals no event.
 */
public final class AccessCodeConnections {

  private final EventRepository events;
  private final ConnectionRepository connections;

  public AccessCodeConnections(EventRepository events, ConnectionRepository connections) {
    this.events = events;
    this.connections = connections;
  }

  /**
   * Connects {@code user} to the public event that gives out {@code typedCode}, or returns their
   * connection to it when they have one. The role is checked before the code, so the answer to
   * anyone else says nothing about the code.
   *
   * @throws NotAllowedException when the user is not an active participant
   * @throws NotFoundException with {@code ACCESS_CODE_NOT_FOUND} when no public event that has not
   *     ended or been cancelled gives out the code
   */
  public JoinedConnection join(User user, String typedCode, Instant now) {
    EventConnection.checkCanConnect(user);
    Event event =
        accessCode(typedCode)
            .flatMap(code -> events.findUpcomingPublicByAccessCode(code, now))
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "ACCESS_CODE_NOT_FOUND", "No event was found with this code."));
    connections.lockConnectionsOf(user.getId());
    Optional<EventConnection> existing =
        connections.findByUserAndEvent(user.getId(), event.getId());
    if (existing.isPresent()) {
      return new JoinedConnection(existing.get(), event, false);
    }
    EventConnection connection = EventConnection.joinWithAccessCode(user, event.getId(), now);
    connections.add(connection);
    return new JoinedConnection(connection, event, true);
  }

  /** The code as events keep it; empty when no event could have it, such as one with spaces. */
  private static Optional<EventCode> accessCode(String typed) {
    try {
      return Optional.of(new EventCode(typed));
    } catch (RuleViolationException e) {
      return Optional.empty();
    }
  }
}
