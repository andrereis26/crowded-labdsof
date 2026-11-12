package com.crowded.core.user.infrastructure.persistence.repository;

import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.user.domain.repository.TokenRevocations;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link TokenRevocations} in Redis (ADR 0004): {@code revoked-before:<userId>} holds the time, in
 * epoch milliseconds, before which the user's tokens are revoked. Notifications reads the same key.
 * It expires with the last token it revokes.
 */
@Repository
class TokenRevocationsRepositoryAdapter implements TokenRevocations {

  static final String PREFIX = "revoked-before:";

  private final StringRedisTemplate redis;
  private final Duration tokenLifetime;

  TokenRevocationsRepositoryAdapter(
      StringRedisTemplate redis, @Value("${crowded.jwt.lifetime:1h}") Duration tokenLifetime) {
    this.redis = redis;
    this.tokenLifetime = tokenLifetime;
  }

  @Override
  public void revokeIssuedBefore(UUID userId, Instant at) {
    try {
      redis
          .opsForValue()
          .set(PREFIX + userId, Long.toString(at.toEpochMilli()), tokenLifetime.plusMinutes(1));
    } catch (DataAccessException e) {
      throw unavailable();
    }
  }

  @Override
  public Optional<Instant> revokedBefore(UUID userId) {
    try {
      return Optional.ofNullable(redis.opsForValue().get(PREFIX + userId))
          .map(millis -> Instant.ofEpochMilli(Long.parseLong(millis)));
    } catch (DataAccessException e) {
      throw unavailable();
    }
  }

  private static UnavailableException unavailable() {
    return new UnavailableException(
        "REVOCATIONS_UNAVAILABLE", "This is not available right now. Try again soon.");
  }
}
