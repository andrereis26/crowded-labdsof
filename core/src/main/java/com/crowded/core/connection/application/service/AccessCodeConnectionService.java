package com.crowded.core.connection.application.service;

import com.crowded.core.connection.application.dto.JoinWithAccessCodeCommand;
import com.crowded.core.connection.application.dto.JoinedEvent;
import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventCode;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connects a participant to a public event with its access code. The event is the one Core finds
 * for the code, never one the client names, and a code that leads to no event gets the same answer
 * whatever the reason, so it reveals no event. The code is never logged.
 */
@Service
public class AccessCodeConnectionService {

  private static final Logger log = LoggerFactory.getLogger(AccessCodeConnectionService.class);

  private final EventRepository events;
  private final UserRepository users;
  private final ConnectionRepository connections;
  private final Clock clock;
  private final MeterRegistry meters;

  AccessCodeConnectionService(
      EventRepository events,
      UserRepository users,
      ConnectionRepository connections,
      Clock clock,
      MeterRegistry meters) {
    this.events = events;
    this.users = users;
    this.connections = connections;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Connects the command's participant to the public event whose access code they sent; when they
   * are connected to it already, returns that connection and adds nothing.
   *
   * @throws NotAllowedException when the user is not an active participant
   * @throws NotFoundException with {@code ACCESS_CODE_NOT_FOUND} when no public event that has not
   *     ended or been cancelled gives out the code
   */
  @Transactional
  public JoinedEvent joinWithAccessCode(JoinWithAccessCodeCommand command) {
    Instant now = clock.instant();
    User user =
        users
            .findById(command.userId())
            .orElseThrow(
                () ->
                    new NotAllowedException(
                        "PARTICIPANT_ONLY", "Only a participant can connect to an event."));
    Event event =
        accessCode(command.accessCode())
            .flatMap(code -> events.findUpcomingPublicByAccessCode(code, now))
            .orElseThrow(
                () -> {
                  log.info("Access code of user {} matched no public event", user.getId());
                  count("NOT_FOUND");
                  return new NotFoundException(
                      "ACCESS_CODE_NOT_FOUND", "No event was found with this code.");
                });

    connections.lockConnectionsOf(user.getId());
    Optional<EventConnection> existing =
        connections.findByUserAndEvent(user.getId(), event.getId());
    EventConnection connection =
        existing.orElseGet(() -> EventConnection.joinWithAccessCode(user, event.getId(), now));
    String outcome = existing.isPresent() ? "ALREADY_CONNECTED" : "CONNECTED";
    if (existing.isEmpty()) {
      connections.add(connection);
    }
    log.info(
        "User {} joined event {} with its access code: outcome={}",
        user.getId(),
        event.getId(),
        outcome);
    count(outcome);
    return new JoinedEvent(
        connection.getId(),
        existing.isEmpty(),
        event.getId(),
        event.getProfile().name(),
        event.getState().schedule().start(),
        event.getState().schedule().doorsOpenAt(),
        event.getState().venue().name());
  }

  /** The code as events keep it; empty when no event could have it, such as one with spaces. */
  private static Optional<EventCode> accessCode(String typed) {
    try {
      return Optional.of(new EventCode(typed));
    } catch (RuleViolationException e) {
      return Optional.empty();
    }
  }

  private void count(String outcome) {
    Counter.builder("crowded.connections.join")
        .description("Attempts to connect to a public event with its access code")
        .tag("outcome", outcome)
        .register(meters)
        .increment();
  }
}
