package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;

/**
 * What an operational alert tells participants, written by the organization. Kept short, as it is
 * read on a phone, on site.
 *
 * @param text stripped, never blank
 */
public record AlertMessage(String text) {

  /** The longest message, in characters. */
  public static final int MAX_LENGTH = 500;

  /**
   * @throws RuleViolationException when the text is missing, blank or too long
   */
  public AlertMessage {
    text = text == null ? "" : text.strip();
    if (text.isEmpty()) {
      throw new RuleViolationException("ALERT_MESSAGE_REQUIRED", "Write the alert's message.");
    }
    if (text.length() > MAX_LENGTH) {
      throw new RuleViolationException(
          "ALERT_MESSAGE_TOO_LONG",
          "The alert's message has at most " + MAX_LENGTH + " characters.");
    }
  }
}
