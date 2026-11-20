package com.crowded.core.user.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.time.Duration;
import java.util.Objects;

/**
 * How long a session lasts (ADR 0021): it ends after {@code idle} without use, and {@code maximum}
 * after sign-in whatever happens.
 */
public record SessionLifetime(Duration idle, Duration maximum) {

  public SessionLifetime {
    Objects.requireNonNull(idle, "idle");
    Objects.requireNonNull(maximum, "maximum");
    if (idle.isNegative() || idle.isZero() || maximum.compareTo(idle) < 0) {
      throw new RuleViolationException(
          "SESSION_LIFETIME_INVALID",
          "A session lasts a positive time without use, and at least as long in total.");
    }
  }
}
