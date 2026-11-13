package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;

/**
 * An entrance or another place where people queue at an event (a door, the ticket office, a bar),
 * named by its organization.
 *
 * @param name stripped, at most {@value #MAX_NAME} characters
 */
public record QueuePoint(String name) {

  static final int MAX_NAME = 60;

  /**
   * @throws RuleViolationException when the name is blank or too long
   */
  public QueuePoint {
    name = name == null ? "" : name.strip();
    if (name.isEmpty()) {
      throw new RuleViolationException(
          "QUEUE_POINT_NAME_REQUIRED", "An entrance or queue point needs its name.");
    }
    if (name.length() > MAX_NAME) {
      throw new RuleViolationException(
          "QUEUE_POINT_NAME_TOO_LONG",
          "The name of an entrance or queue point has at most " + MAX_NAME + " characters.");
    }
  }

  /** Whether {@code other} has the same name, ignoring case. */
  boolean isNamedLike(QueuePoint other) {
    return name.equalsIgnoreCase(other.name);
  }
}
