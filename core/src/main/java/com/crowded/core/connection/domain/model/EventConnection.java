package com.crowded.core.connection.domain.model;

import com.crowded.core.shared.domain.exception.NotAllowedException;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A participant's connection to an event, which lets them see its page and updates. It keeps the
 * event, never the code used, so a participant keeps access when the organization changes the code;
 * removing it ends the access.
 */
public final class EventConnection {

  private final UUID id;
  private final UUID userId;
  private final UUID eventId;
  private final Instant connectedAt;

  private EventConnection(UUID id, UUID userId, UUID eventId, Instant connectedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.connectedAt = Objects.requireNonNull(connectedAt, "connectedAt");
  }

  /**
   * Lets through who may connect to an event: an active participant.
   *
   * @throws NotAllowedException otherwise
   */
  public static void checkCanConnect(User user) {
    if (!user.isParticipant()) {
      throw new NotAllowedException(
          "PARTICIPANT_ONLY", "Only a participant can connect to an event.");
    }
  }

  /**
   * Connects {@code user} to the public event {@code eventId}, found by its access code.
   *
   * @throws NotAllowedException when the user is not an active participant
   */
  public static EventConnection joinWithAccessCode(User user, UUID eventId, Instant now) {
    checkCanConnect(user);
    return new EventConnection(UUID.randomUUID(), user.getId(), eventId, now);
  }

  /**
   * Connects {@code user} to the ticketed event {@code eventId}, which their imported ticket
   * matched.
   *
   * @throws NotAllowedException when the user is not an active participant
   */
  public static EventConnection connectWithTicket(User user, UUID eventId, Instant now) {
    checkCanConnect(user);
    return new EventConnection(UUID.randomUUID(), user.getId(), eventId, now);
  }

  /** A stored connection, as it was saved. */
  public static EventConnection restore(UUID id, UUID userId, UUID eventId, Instant connectedAt) {
    return new EventConnection(id, userId, eventId, connectedAt);
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public UUID getEventId() {
    return eventId;
  }

  public Instant getConnectedAt() {
    return connectedAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof EventConnection connection && id.equals(connection.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "EventConnection[" + id + "]";
  }
}
