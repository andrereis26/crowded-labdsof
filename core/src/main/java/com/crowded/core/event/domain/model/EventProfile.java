package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;

/**
 * What participants read about an event: its name and description, stripped.
 *
 * @param name the event's name, at most {@value #MAX_NAME} characters
 * @param description what the event is, at most {@value #MAX_DESCRIPTION} characters
 */
public record EventProfile(String name, String description) {

  static final int MAX_NAME = 120;
  static final int MAX_DESCRIPTION = 2000;

  /**
   * @throws RuleViolationException when the name or the description is blank or too long
   */
  public EventProfile {
    name = name == null ? "" : name.strip();
    description = description == null ? "" : description.strip();
    if (name.isEmpty()) {
      throw new RuleViolationException("NAME_REQUIRED", "An event needs its name.");
    }
    if (name.length() > MAX_NAME) {
      throw new RuleViolationException(
          "NAME_TOO_LONG", "The name has at most " + MAX_NAME + " characters.");
    }
    if (description.isEmpty()) {
      throw new RuleViolationException("DESCRIPTION_REQUIRED", "An event needs its description.");
    }
    if (description.length() > MAX_DESCRIPTION) {
      throw new RuleViolationException(
          "DESCRIPTION_TOO_LONG",
          "The description has at most " + MAX_DESCRIPTION + " characters.");
    }
  }
}
