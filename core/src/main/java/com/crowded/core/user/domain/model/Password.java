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
   * @throws RuleViolationException when the password is shorter than 8 characters, longer than 72
   *     bytes, or lacks an uppercase letter, a lowercase letter, a digit or a special character
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
    if (!isStrong(value)) {
      throw new RuleViolationException(
          "PASSWORD_TOO_WEAK",
          "The password needs an uppercase letter, a lowercase letter, a digit and a special"
              + " character.");
    }
  }

  /**
   * Whether {@code value} has an uppercase letter, a lowercase letter, a digit and a special
   * character: anything else that is not a space, such as {@code -}, {@code !} or {@code €}.
   */
  public static boolean isStrong(String value) {
    boolean upper = false;
    boolean lower = false;
    boolean digit = false;
    boolean special = false;
    for (int i = 0; i < value.length(); i = value.offsetByCodePoints(i, 1)) {
      int c = value.codePointAt(i);
      upper |= Character.isUpperCase(c);
      lower |= Character.isLowerCase(c);
      digit |= Character.isDigit(c);
      special |= !Character.isLetterOrDigit(c) && !Character.isWhitespace(c);
    }
    return upper && lower && digit && special;
  }

  @Override
  public String toString() {
    return "Password[hidden]";
  }
}
