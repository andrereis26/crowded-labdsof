package com.crowded.core.connection.application.service;

import com.crowded.core.connection.application.dto.JoinWithAccessCodeCommand;
import com.crowded.core.connection.application.dto.JoinedEvent;
import com.crowded.core.connection.domain.model.JoinedConnection;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.connection.domain.service.AccessCodeConnections;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Connects a participant to a public event with its access code ({@link AccessCodeConnections}),
 * and logs and counts the outcome. The code is never logged.
 */
@Service
public class AccessCodeConnectionService {

  private static final Logger log = LoggerFactory.getLogger(AccessCodeConnectionService.class);

  private final AccessCodeConnections joining;
  private final UserRepository users;
  private final Clock clock;
  private final MeterRegistry meters;

  AccessCodeConnectionService(
      EventRepository events,
      UserRepository users,
      ConnectionRepository connections,
      Clock clock,
      MeterRegistry meters) {
    this.joining = new AccessCodeConnections(events, connections);
    this.users = users;
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
    User user =
        users
            .findById(command.userId())
            .orElseThrow(
                () ->
                    new NotAllowedException(
                        "PARTICIPANT_ONLY", "Only a participant can connect to an event."));
    JoinedConnection joined;
    try {
      joined = joining.join(user, command.accessCode(), clock.instant());
    } catch (NotFoundException e) {
      log.info("Access code of user {} matched no public event", user.getId());
      count("NOT_FOUND");
      throw e;
    }
    String outcome = joined.created() ? "CONNECTED" : "ALREADY_CONNECTED";
    Event event = joined.event();
    log.info(
        "User {} joined event {} with its access code: outcome={}",
        user.getId(),
        event.getId(),
        outcome);
    count(outcome);
    return new JoinedEvent(
        joined.connection().getId(),
        joined.created(),
        event.getId(),
        event.getProfile().name(),
        event.getState().schedule().start(),
        event.getState().schedule().doorsOpenAt(),
        event.getState().venue().name());
  }

  private void count(String outcome) {
    Counter.builder("crowded.connections.join")
        .description("Attempts to connect to a public event with its access code")
        .tag("outcome", outcome)
        .register(meters)
        .increment();
  }
}
