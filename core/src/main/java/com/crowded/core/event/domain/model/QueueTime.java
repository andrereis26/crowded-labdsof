package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.Objects;

/**
 * The waiting time participants read at a queue point, with who gave it and when.
 *
 * @param waitMinutes from 0 to {@value #MAX_MINUTES}
 */
public record QueueTime(int waitMinutes, Provenance provenance) {

  static final int MAX_MINUTES = 600;

  /**
   * @throws RuleViolationException when the minutes are out of range
   */
  public QueueTime {
    checkMinutes(waitMinutes);
    Objects.requireNonNull(provenance, "provenance");
  }

  /** Refuses a negative wait, or one longer than any queue at an event. */
  static void checkMinutes(int waitMinutes) {
    if (waitMinutes < 0 || waitMinutes > MAX_MINUTES) {
      throw new RuleViolationException(
          "WAIT_MINUTES_INVALID",
          "A waiting time is a whole number of minutes from 0 to " + MAX_MINUTES + ".");
    }
  }
}
