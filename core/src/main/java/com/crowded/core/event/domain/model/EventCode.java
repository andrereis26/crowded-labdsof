package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * The code that connects a participant to an event: printed on its tickets, or given out by its
 * organization. Codes are compared without case, so they are kept in upper case.
 *
 * @param value letters, digits and hyphens, from {@value #MIN} to {@value #MAX} characters
 */
public record EventCode(String value) {

  static final int MIN = 4;
  static final int MAX = 64;
  private static final Pattern FORMAT = Pattern.compile("[A-Z0-9]+(-[A-Z0-9]+)*");

  /**
   * @throws RuleViolationException when it is blank, too short or long, or has other characters
   */
  public EventCode {
    value = value == null ? "" : value.strip().toUpperCase(Locale.ROOT);
    if (value.isEmpty()) {
      throw new RuleViolationException("CODE_REQUIRED", "An event needs its code.");
    }
    if (value.length() < MIN || value.length() > MAX || !FORMAT.matcher(value).matches()) {
      throw new RuleViolationException(
          "CODE_INVALID",
          "A code has "
              + MIN
              + " to "
              + MAX
              + " letters, digits or hyphens, without spaces or accents.");
    }
  }
}
