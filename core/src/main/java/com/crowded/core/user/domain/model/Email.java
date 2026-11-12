package com.crowded.core.user.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * A sign-in email, stripped and in lower case, so two spellings of one address are one account.
 *
 * @param value the normalized address
 */
public record Email(String value) {

  /** The longest address SMTP allows. */
  public static final int MAX_LENGTH = 254;

  private static final Pattern SHAPE = Pattern.compile("[^@\\s]+@[^@\\s]+");

  /**
   * @throws RuleViolationException when {@code value} is not an email address
   */
  public Email {
    value = normalize(value);
    if (!isValid(value)) {
      throw new RuleViolationException("EMAIL_INVALID", "Give a valid email address.");
    }
  }

  /** The email in {@code value}, or empty when it is not one. */
  public static Optional<Email> parse(String value) {
    String normalized = normalize(value);
    return isValid(normalized) ? Optional.of(new Email(normalized)) : Optional.empty();
  }

  /** {@code value} stripped and in lower case; empty for null. */
  public static String normalize(String value) {
    return value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
  }

  private static boolean isValid(String normalized) {
    return normalized.length() <= MAX_LENGTH && SHAPE.matcher(normalized).matches();
  }

  @Override
  public String toString() {
    return value;
  }
}
