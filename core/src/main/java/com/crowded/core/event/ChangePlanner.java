package com.crowded.core.event;

import com.crowded.core.event.ChangeRequest.NewValue;
import com.crowded.core.web.ApiException;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.Set;
import org.springframework.http.HttpStatus;

/**
 * Decides what an official change does to an event (FR-016, FR-072, FR-074, FR-075): its new
 * status, schedule or venue, and its severity. Pure, so every rule is a unit test.
 */
final class ChangePlanner {

  /** Events that no longer take changes. A proposed event has no schedule to change yet. */
  private static final Set<EventStatus> CLOSED =
      Set.of(EventStatus.PROPOSED, EventStatus.CANCELLED, EventStatus.ENDED);

  private ChangePlanner() {}

  /**
   * The plan for {@code request}, or empty when the event already is as the request asks, such as a
   * change sent twice.
   *
   * @throws ApiException when the request is invalid or the event takes no more changes
   */
  static Optional<ChangePlan> plan(EventState before, ChangeRequest request, Instant now) {
    NewValue value = request.newValue() == null ? NewValue.NONE : request.newValue();
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
          default ->
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

  private static EventState postponed(EventState before, NewValue value) {
    Instant start = instant(value.startDateTime());
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

  private static EventState retimed(EventState before, NewValue value) {
    if (value.startDateTime() == null && value.doorsOpenAt() == null) {
      throw invalid("NEW_TIME_REQUIRED", "A time change needs the new start or doors opening.");
    }
    Instant start = or(instant(value.startDateTime()), before.startDateTime());
    Duration shift = Duration.between(before.startDateTime(), start);
    return new EventState(
        before.status(),
        start,
        or(instant(value.doorsOpenAt()), before.doorsOpenAt()),
        orShifted(value.endDateTime(), before.endDateTime(), shift),
        before.venueName(),
        before.address(),
        before.latitude(),
        before.longitude());
  }

  private static EventState relocated(EventState before, NewValue value) {
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

  private static EventState withStatus(EventState before, EventStatus status) {
    return new EventState(
        status,
        before.startDateTime(),
        before.doorsOpenAt(),
        before.endDateTime(),
        before.venueName(),
        before.address(),
        before.latitude(),
        before.longitude());
  }

  private static boolean unchangedOrSame(OffsetDateTime value, Instant current) {
    return value == null || value.toInstant().equals(current);
  }

  private static Instant orShifted(OffsetDateTime value, Instant current, Duration shift) {
    if (value != null) {
      return value.toInstant();
    }
    return current == null ? null : current.plus(shift);
  }

  private static Instant or(Instant value, Instant current) {
    return value == null ? current : value;
  }

  private static Instant instant(OffsetDateTime value) {
    return value == null ? null : value.toInstant();
  }

  private static String text(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }

  private static ApiException invalid(String code, String detail) {
    return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
  }

  private static ApiException closed() {
    return new ApiException(
        HttpStatus.CONFLICT, "EVENT_CLOSED", "This event is cancelled, ended or not scheduled.");
  }
}
