package com.crowded.core.user.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.nio.charset.StandardCharsets;

/**
 * A password a user chose, before it is hashed. Never logged: {@link #toString()} hides it.
 *
 * @param value the password as typed
 */
public record Password(String value) {

  public static final int MIN_LENGTH = 8;

  /** BCrypt reads only the first 72 bytes. */
  public static final int MAX_BYTES = 72;

  /**
   * @throws RuleViolationException when the password is shorter than 8 characters or longer than 72
   *     bytes
   */
  public Password {
    if (value == null || value.length() < MIN_LENGTH) {
      throw new RuleViolationException(
          "PASSWORD_TOO_SHORT", "The password needs at least 8 characters.");
    }
    if (value.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
      throw new RuleViolationException(
          "PASSWORD_TOO_LONG", "The password can have at most 72 bytes.");
    }
  }

  @Override
  public String toString() {
    return "Password[hidden]";
  }
}
