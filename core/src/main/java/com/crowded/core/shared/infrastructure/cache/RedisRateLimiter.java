package com.crowded.core.shared.infrastructure.cache;

import com.crowded.core.shared.application.port.RateLimiter;
import com.crowded.core.shared.domain.exception.UnavailableException;
import java.time.Duration;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Fixed-window counters in Redis (ADR 0004): the first attempt creates the key with the window as
 * its expiry. When Redis is down, attempts are refused rather than let through unlimited.
 */
@Component
class RedisRateLimiter implements RateLimiter {

  private static final String PREFIX = "rate-limit:";

  private final StringRedisTemplate redis;

  RedisRateLimiter(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public boolean tryAcquire(String key, int limit, Duration window) {
    String counter = PREFIX + key;
    try {
      Long attempts = redis.opsForValue().increment(counter);
      // The first attempt starts the window. A counter left without expiry, if setting it
      // failed once, would block for good: it gets one when it is over the limit.
      if (attempts != null
          && (attempts == 1
              || attempts > limit && Long.valueOf(-1).equals(redis.getExpire(counter)))) {
        redis.expire(counter, window);
      }
      return attempts != null && attempts <= limit;
    } catch (DataAccessException e) {
      throw new UnavailableException(
          "RATE_LIMIT_UNAVAILABLE", "This is not available right now. Try again soon.");
    }
  }
}
