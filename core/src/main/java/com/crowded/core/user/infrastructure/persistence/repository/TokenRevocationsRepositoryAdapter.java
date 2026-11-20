package com.crowded.core.user.infrastructure.persistence.repository;

import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.user.domain.repository.TokenRevocations;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

/**
 * {@link TokenRevocations} in Redis (ADR 0004, ADR 0021): {@code revoked-before:<userId>} holds the
 * time, in epoch milliseconds, before which the user's tokens are revoked, and {@code
 * revoked-token:<jti>} marks one signed-out token. Notifications reads the same keys. Each expires
 * with the last token it revokes.
 */
@Repository
class TokenRevocationsRepositoryAdapter implements TokenRevocations {

  static final String PREFIX = "revoked-before:";
  static final String TOKEN_PREFIX = "revoked-token:";

  private final StringRedisTemplate redis;
  private final Duration tokenLifetime;
  private final Clock clock;

  TokenRevocationsRepositoryAdapter(
      StringRedisTemplate redis,
      @Value("${crowded.jwt.lifetime:15m}") Duration tokenLifetime,
      Clock clock) {
    this.redis = redis;
    this.tokenLifetime = tokenLifetime;
    this.clock = clock;
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

  @Override
  public void revokeToken(String tokenId, Instant expiresAt) {
    Duration remaining = Duration.between(clock.instant(), expiresAt);
    if (remaining.isNegative() || remaining.isZero()) {
      return;
    }
    try {
      // A minute more, so a clock slightly behind Core's still sees the revocation.
      redis.opsForValue().set(TOKEN_PREFIX + tokenId, "1", remaining.plusMinutes(1));
    } catch (DataAccessException e) {
      throw unavailable();
    }
  }

  @Override
  public boolean isTokenRevoked(String tokenId) {
    try {
      return Boolean.TRUE.equals(redis.hasKey(TOKEN_PREFIX + tokenId));
    } catch (DataAccessException e) {
      throw unavailable();
    }
  }

  private static UnavailableException unavailable() {
    return new UnavailableException(
        "REVOCATIONS_UNAVAILABLE", "This is not available right now. Try again soon.");
  }
}
