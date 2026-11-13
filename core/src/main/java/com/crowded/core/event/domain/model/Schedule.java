package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Duration;
import java.time.Instant;

/**
 * When an event happens: the doors open before it starts, and it ends after it starts.
 *
 * @param start when the event starts
 * @param doorsOpenAt when the doors open, or null when not announced
 * @param end when it ends, or null when not announced
 */
public record Schedule(Instant start, Instant doorsOpenAt, Instant end) {

  /**
   * @throws RuleViolationException when the start is missing, the doors open after it or the event
   *     ends before it starts
   */
  public Schedule {
    if (start == null) {
      throw new RuleViolationException("START_REQUIRED", "An event needs the time it starts.");
    }
    if (doorsOpenAt != null && doorsOpenAt.isAfter(start)) {
      throw new RuleViolationException(
          "DOORS_AFTER_START", "The doors must open before the event starts.");
    }
    if (end != null && end.isBefore(start)) {
      throw new RuleViolationException("END_BEFORE_START", "The event must end after it starts.");
    }
  }

  /**
   * The schedule moved to {@code newStart}. The doors and the end move by as much, unless given.
   *
   * @param newDoorsOpenAt the new doors opening, or null to move the current one
   * @param newEnd the new end, or null to move the current one
   */
  public Schedule movedTo(Instant newStart, Instant newDoorsOpenAt, Instant newEnd) {
    Duration shift = Duration.between(start, newStart);
    return new Schedule(
        newStart, orShifted(newDoorsOpenAt, doorsOpenAt, shift), orShifted(newEnd, end, shift));
  }

  /**
   * The schedule with a new start or doors opening, each kept when null. The end keeps the event's
   * length unless given; the doors stay where they are unless given.
   */
  public Schedule retimed(Instant newStart, Instant newDoorsOpenAt, Instant newEnd) {
    Instant nextStart = newStart == null ? start : newStart;
    Duration shift = Duration.between(start, nextStart);
    return new Schedule(
        nextStart,
        newDoorsOpenAt == null ? doorsOpenAt : newDoorsOpenAt,
        orShifted(newEnd, end, shift));
  }

  private static Instant orShifted(Instant value, Instant current, Duration shift) {
    if (value != null) {
      return value;
    }
    return current == null ? null : current.plus(shift);
  }
}
