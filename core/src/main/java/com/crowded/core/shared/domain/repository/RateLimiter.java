package com.crowded.core.shared.domain.repository;

import java.time.Duration;

/** Counts attempts per key in a fixed window, shared by every Core instance (ADR 0004). */
public interface RateLimiter {

  /**
   * Counts one attempt under {@code key} and says whether it is within {@code limit} attempts in
   * the window that started with the first one.
   *
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when the attempts cannot
   *     be counted, so nothing is let through unlimited
   */
  boolean tryAcquire(String key, int limit, Duration window);
}
