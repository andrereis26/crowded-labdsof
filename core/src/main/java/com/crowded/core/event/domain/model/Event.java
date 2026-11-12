package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * An event, as far as official changes need it; #36 adds the rest. Its state changes only through
 * {@link #publishChange}, which applies the rules of an official change (FR-016, FR-072, FR-074,
 * FR-075).
 */
public final class Event {

  /** Events that no longer take changes. */
  private static final Set<EventStatus> CLOSED = Set.of(EventStatus.CANCELLED, EventStatus.ENDED);

  private final UUID id;
  private final UUID organizationId;
  private EventState state;

  private Event(UUID id, UUID organizationId, EventState state) {
    this.id = Objects.requireNonNull(id, "id");
    this.organizationId = organizationId;
    this.state = Objects.requireNonNull(state, "state");
  }

  /**
   * A stored event, as it was saved.
   *
   * @param organizationId the owning organization; null for an event no organization owns
   */
  public static Event restore(UUID id, UUID organizationId, EventState state) {
    return new Event(id, organizationId, state);
  }

  /**
   * Applies the official change {@code request}, published by {@code provenance}, and returns it;
   * empty when the event already is as the request asks, such as a change sent twice.
   *
   * @throws RuleViolationException when the request is invalid
   * @throws ConflictException when the event takes no more changes
   */
  public Optional<EventChange> publishChange(
      RequestedChange request, Provenance provenance, Instant now) {
    Optional<ChangePlan> plan = plan(request, now);
    plan.ifPresent(p -> state = p.after());
    return plan.map(p -> EventChange.publish(id, p, provenance));
  }

  /**
   * The change a repeated request matches: {@code latest}, the latest published change of its type,
   * when this event still has its values.
   *
   * @throws RuleViolationException when there is no such change, so the request changes nothing
   */
  public EventChange repeatedChange(Optional<EventChange> latest) {
    return latest
        .filter(change -> change.getEventId().equals(id) && change.isReflectedIn(state))
        .orElseThrow(
            () ->
                new RuleViolationException(
                    "NOTHING_CHANGES", "The event already has these values."));
  }

  /** What {@code request} would do to this event, without doing it; empty when nothing changes. */
  Optional<ChangePlan> plan(RequestedChange request, Instant now) {
    NewValues value = request.newValues() == null ? NewValues.NONE : request.newValues();
    boolean ticketInfo = request.ticketStillValid() != null || text(request.refundInfo()) != null;
    if (ticketInfo
        && request.type() != ChangeType.POSTPONEMENT
        && request.type() != ChangeType.CANCELLATION) {
      throw new RuleViolationException(
          "TICKET_INFO_NOT_APPLICABLE",
          "Ticket validity and refund information apply only to a postponement or cancellation.");
    }
    EventState after =
        switch (request.type()) {
          case CANCELLATION -> state.inStatus(EventStatus.CANCELLED);
          case POSTPONEMENT -> postponed(value, now);
          case TIME_CHANGE -> retimed(value, now);
          case VENUE_CHANGE -> relocated(value);
          case DETAILS_UPDATE ->
              throw new RuleViolationException(
                  "UNSUPPORTED_CHANGE_TYPE",
                  "Publish a postponement, a time change, a venue change or a cancellation.");
        };
    if (after.equals(state)) {
      return Optional.empty();
    }
    if (CLOSED.contains(state.status())) {
      throw new ConflictException("EVENT_CLOSED", "This event is cancelled or ended.");
    }
    return Optional.of(
        new ChangePlan(
            request.type(),
            severity(request.type(), after),
            state,
            after,
            request.ticketStillValid(),
            text(request.refundInfo()),
            text(request.message())));
  }

  private EventState postponed(NewValues value, Instant now) {
    Schedule schedule = state.schedule();
    Instant start = value.startDateTime();
    if (start == null) {
      throw new RuleViolationException("START_REQUIRED", "A postponement needs the new start.");
    }
    if (start.equals(schedule.start())
        && unchangedOrSame(value.doorsOpenAt(), schedule.doorsOpenAt())
        && unchangedOrSame(value.endDateTime(), schedule.end())) {
      // The postponement was already applied; its status is already POSTPONED.
      return state;
    }
    if (!start.isAfter(schedule.start())) {
      throw new RuleViolationException(
          "POSTPONEMENT_NOT_LATER", "A postponement moves the start later; use a time change.");
    }
    checkFuture(start, now);
    return new EventState(
        EventStatus.POSTPONED,
        schedule.movedTo(start, value.doorsOpenAt(), value.endDateTime()),
        state.venue());
  }

  private EventState retimed(NewValues value, Instant now) {
    if (value.startDateTime() == null && value.doorsOpenAt() == null) {
      throw new RuleViolationException(
          "NEW_TIME_REQUIRED", "A time change needs the new start or doors opening.");
    }
    if (value.startDateTime() != null && !value.startDateTime().equals(state.schedule().start())) {
      checkFuture(value.startDateTime(), now);
    }
    return new EventState(
        state.status(),
        state.schedule().retimed(value.startDateTime(), value.doorsOpenAt(), value.endDateTime()),
        state.venue());
  }

  private EventState relocated(NewValues value) {
    Venue venue =
        new Venue(
            value.venueName(),
            value.address(),
            Coordinates.ofNullable(value.latitude(), value.longitude()));
    if (venue.isSamePlaceAs(state.venue())) {
      return state;
    }
    // Coordinates of the old venue would point participants to the wrong place.
    return new EventState(EventStatus.RELOCATED, state.schedule(), venue);
  }

  /** Critical: cancellation, postponement, venue or start-time change; else a warning (FR-074). */
  private Severity severity(ChangeType type, EventState after) {
    if (type == ChangeType.TIME_CHANGE
        && after.schedule().start().equals(state.schedule().start())) {
      return Severity.WARNING;
    }
    return Severity.CRITICAL;
  }

  /** Checked before the new schedule, so a start in the past is reported as such. */
  private static void checkFuture(Instant start, Instant now) {
    if (!start.isAfter(now)) {
      throw new RuleViolationException("START_IN_PAST", "The new start must be in the future.");
    }
  }

  private static boolean unchangedOrSame(Instant value, Instant current) {
    return value == null || value.equals(current);
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  public UUID getId() {
    return id;
  }

  /** The owning organization; null for an event no organization owns. */
  public UUID getOrganizationId() {
    return organizationId;
  }

  public EventState getState() {
    return state;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Event event && id.equals(event.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Event[" + id + ", " + state.status() + "]";
  }
}
