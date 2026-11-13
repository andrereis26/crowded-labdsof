package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;

/** How many people the venue holds, from which the crowd level is read. */
public record Capacity(int people) {

  static final int MAX = 1_000_000;

  /**
   * @throws RuleViolationException when it is not between 1 and {@value #MAX}
   */
  public Capacity {
    if (people < 1 || people > MAX) {
      throw new RuleViolationException(
          "CAPACITY_OUT_OF_RANGE", "The capacity must be between 1 and " + MAX + " people.");
    }
  }
}
