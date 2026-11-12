package com.crowded.core.event.application.service;

import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.shared.domain.exception.NotFoundException;
import com.crowded.core.user.domain.model.User;
import com.crowded.core.user.domain.repository.UserRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Who may see or manage an event. Every endpoint about one event calls it first: the role and the
 * organization come from the database, never from the token, and the event's connections decide
 * which participants see it.
 */
@Service
public class EventAccess {

  private static final Logger log = LoggerFactory.getLogger(EventAccess.class);

  private final EventRepository events;
  private final UserRepository users;
  private final ConnectionRepository connections;
  private final MeterRegistry meters;

  EventAccess(
      EventRepository events,
      UserRepository users,
      ConnectionRepository connections,
      MeterRegistry meters) {
    this.events = events;
    this.users = users;
    this.connections = connections;
    this.meters = meters;
  }

  /**
   * The event {@code userId} may see: its page, history and live updates.
   *
   * @throws NotFoundException when the event does not exist
   * @throws NotAllowedException when the user is not connected to it nor a member of its
   *     organization
   */
  @Transactional(readOnly = true)
  public Event viewable(UUID userId, UUID eventId) {
    Event event =
        events
            .findById(eventId)
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
    Optional<User> user = users.findById(userId);
    try {
      if (user.isEmpty()) {
        throw new NotAllowedException(
            "NOT_CONNECTED", "Only participants connected to this event can see it.");
      }
      event.checkCanBeSeenBy(user.get(), connections.isConnected(userId, eventId));
      return event;
    } catch (NotAllowedException e) {
      refused("view", userId, eventId);
      throw e;
    }
  }

  /**
   * Checks that {@code userId} may manage {@code event}.
   *
   * @throws NotAllowedException when the user is not an active member of its organization
   */
  public void checkManager(UUID userId, Event event) {
    try {
      event.checkCanBeManagedBy(
          users
              .findById(userId)
              .orElseThrow(
                  () ->
                      new NotAllowedException(
                          "NOT_EVENT_ORGANIZATION",
                          "Only active members of the organization that owns this event can"
                              + " change it.")));
    } catch (NotAllowedException e) {
      refused("manage", userId, event.getId());
      throw e;
    }
  }

  private void refused(String access, UUID userId, UUID eventId) {
    log.warn("Refused to let user {} {} event {}", userId, access, eventId);
    Counter.builder("crowded.access.refused")
        .description("Requests refused because of the user's role or connection")
        .tag("access", access)
        .register(meters)
        .increment();
  }
}
