package com.crowded.core.event.domain.service;

import com.crowded.core.event.domain.model.ChangePlan;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.NewValues;
import com.crowded.core.event.domain.model.RequestedChange;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.shared.domain.exception.ConflictException;
import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

/**
 * Decides what an official change does to an event (FR-016, FR-072, FR-074, FR-075): its new
 * status, schedule or venue, and its severity. Pure, so every rule is a unit test.
 */
public final class ChangePlanner {

  /** Events that no longer take changes. A proposed event has no schedule to change yet. */
  private static final Set<EventStatus> CLOSED =
      Set.of(EventStatus.PROPOSED, EventStatus.CANCELLED, EventStatus.ENDED);

  private ChangePlanner() {}

  /**
   * The plan for {@code request}, or empty when the event already is as the request asks, such as a
   * change sent twice.
   *
   * @throws RuleViolationException when the request is invalid
   * @throws ConflictException when the event takes no more changes
   */
  public static Optional<ChangePlan> plan(EventState before, RequestedChange request, Instant now) {
    NewValues value = request.newValues() == null ? NewValues.NONE : request.newValues();
    boolean ticketInfo = request.ticketStillValid() != null || text(request.refundInfo()) != null;
    if (ticketInfo
        && request.type() != ChangeType.POSTPONEMENT
        && request.type() != ChangeType.CANCELLATION) {
      throw invalid(
          "TICKET_INFO_NOT_APPLICABLE",
          "Ticket validity and refund information apply only to a postponement or cancellation.");
    }
    if (before.startDateTime() == null) {
      throw closed();
    }
    EventState after =
        switch (request.type()) {
          case CANCELLATION -> withStatus(before, EventStatus.CANCELLED);
          case POSTPONEMENT -> postponed(before, value);
          case TIME_CHANGE -> retimed(before, value);
          case VENUE_CHANGE -> relocated(before, value);
          case CONFIRMATION, DETAILS_UPDATE ->
              throw invalid(
                  "UNSUPPORTED_CHANGE_TYPE",
                  "Publish a postponement, a time change, a venue change or a cancellation.");
        };
    if (after.equals(before)) {
      return Optional.empty();
    }
    if (CLOSED.contains(before.status())) {
      throw closed();
    }
    checkSchedule(before, after, now);
    return Optional.of(
        new ChangePlan(
            request.type(),
            severity(request.type(), before, after),
            before,
            after,
            request.ticketStillValid(),
            text(request.refundInfo()),
            text(request.message())));
  }

  private static EventState postponed(EventState before, NewValues value) {
    Instant start = value.startDateTime();
    if (start == null) {
      throw invalid("START_REQUIRED", "A postponement needs the new start.");
    }
    if (start.equals(before.startDateTime())
        && unchangedOrSame(value.doorsOpenAt(), before.doorsOpenAt())
        && unchangedOrSame(value.endDateTime(), before.endDateTime())) {
      // The postponement was already applied; its status is already POSTPONED.
      return before;
    }
    if (!start.isAfter(before.startDateTime())) {
      throw invalid(
          "POSTPONEMENT_NOT_LATER", "A postponement moves the start later; use a time change.");
    }
    Duration shift = Duration.between(before.startDateTime(), start);
    return new EventState(
        EventStatus.POSTPONED,
        start,
        orShifted(value.doorsOpenAt(), before.doorsOpenAt(), shift),
        orShifted(value.endDateTime(), before.endDateTime(), shift),
        before.venueName(),
        before.address(),
        before.latitude(),
        before.longitude());
  }

  private static EventState retimed(EventState before, NewValues value) {
    if (value.startDateTime() == null && value.doorsOpenAt() == null) {
      throw invalid("NEW_TIME_REQUIRED", "A time change needs the new start or doors opening.");
    }
    Instant start = or(value.startDateTime(), before.startDateTime());
    Duration shift = Duration.between(before.startDateTime(), start);
    return new EventState(
        before.status(),
        start,
        or(value.doorsOpenAt(), before.doorsOpenAt()),
        orShifted(value.endDateTime(), before.endDateTime(), shift),
        before.venueName(),
        before.address(),
        before.latitude(),
        before.longitude());
  }

  private static EventState relocated(EventState before, NewValues value) {
    String venue = text(value.venueName());
    String address = text(value.address());
    if (venue == null || address == null) {
      throw invalid("VENUE_REQUIRED", "A venue change needs the new venue's name and address.");
    }
    if ((value.latitude() == null) != (value.longitude() == null)) {
      throw invalid("COORDINATES_INCOMPLETE", "Send both latitude and longitude, or neither.");
    }
    if (venue.equals(before.venueName())
        && address.equals(before.address())
        && (value.latitude() == null
            || value.latitude().equals(before.latitude())
                && value.longitude().equals(before.longitude()))) {
      return before;
    }
    // Coordinates of the old venue would point participants to the wrong place.
    return new EventState(
        EventStatus.RELOCATED,
        before.startDateTime(),
        before.doorsOpenAt(),
        before.endDateTime(),
        venue,
        address,
        value.latitude(),
        value.longitude());
  }

  private static void checkSchedule(EventState before, EventState after, Instant now) {
    if (!after.startDateTime().equals(before.startDateTime())
        && !after.startDateTime().isAfter(now)) {
      throw invalid("START_IN_PAST", "The new start must be in the future.");
    }
    if (after.doorsOpenAt() != null && after.doorsOpenAt().isAfter(after.startDateTime())) {
      throw invalid("DOORS_AFTER_START", "The doors must open before the event starts.");
    }
    if (after.endDateTime() != null && after.endDateTime().isBefore(after.startDateTime())) {
      throw invalid("END_BEFORE_START", "The event must end after it starts.");
    }
  }

  /** Critical: cancellation, postponement, venue or start-time change; else a warning (FR-074). */
  private static Severity severity(ChangeType type, EventState before, EventState after) {
    if (type == ChangeType.TIME_CHANGE && after.startDateTime().equals(before.startDateTime())) {
      return Severity.WARNING;
    }
    return Severity.CRITICAL;
  }

  private static EventState withStatus(EventState state, EventStatus status) {
    return new EventState(
        status,
        state.startDateTime(),
        state.doorsOpenAt(),
        state.endDateTime(),
        state.venueName(),
        state.address(),
        state.latitude(),
        state.longitude());
  }

  private static boolean unchangedOrSame(Instant value, Instant current) {
    return value == null || value.equals(current);
  }

  private static Instant orShifted(Instant value, Instant current, Duration shift) {
    if (value != null) {
      return value;
    }
    return current == null ? null : current.plus(shift);
  }

  private static Instant or(Instant value, Instant current) {
    return value == null ? current : value;
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static RuleViolationException invalid(String code, String detail) {
    return new RuleViolationException(code, detail);
  }

  private static ConflictException closed() {
    return new ConflictException(
        "EVENT_CLOSED", "This event is cancelled, ended or not scheduled.");
  }
}
