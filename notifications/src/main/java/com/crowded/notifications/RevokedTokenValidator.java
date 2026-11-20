package com.crowded.notifications;

import java.time.Instant;
import java.util.UUID;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Refuses a token issued before Core revoked its user's tokens (ADR 0004), for example after an
 * administrator deactivated a member, and a token its user signed out with (ADR 0021). Core writes
 * {@code revoked-before:<userId>} in Redis, in epoch milliseconds, and {@code revoked-token:<jti>}.
 * When Redis cannot be read, the token is refused: the services fail safe.
 */
class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

  static final String PREFIX = "revoked-before:";
  static final String TOKEN_PREFIX = "revoked-token:";

  /** When Core issued the token, in epoch milliseconds; {@code iat} has whole seconds. */
  static final String ISSUED_AT_MILLIS = "iat_ms";

  private final StringRedisTemplate redis;

  RevokedTokenValidator(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public OAuth2TokenValidatorResult validate(Jwt token) {
    UUID userId;
    try {
      String subject = token.getSubject();
      userId = subject == null ? null : UUID.fromString(subject);
    } catch (IllegalArgumentException e) {
      userId = null;
    }
    if (userId == null) {
      // Not a user's token: the endpoints refuse it.
      return OAuth2TokenValidatorResult.success();
    }
    String revokedBefore;
    boolean signedOut;
    try {
      revokedBefore = redis.opsForValue().get(PREFIX + userId);
      signedOut =
          token.getId() != null && Boolean.TRUE.equals(redis.hasKey(TOKEN_PREFIX + token.getId()));
    } catch (DataAccessException e) {
      return failure("The token's status cannot be checked.");
    }
    if (signedOut) {
      return failure("The token was revoked.");
    }
    if (revokedBefore != null
        && issuedAt(token).isBefore(Instant.ofEpochMilli(Long.parseLong(revokedBefore)))) {
      return failure("The token was revoked.");
    }
    return OAuth2TokenValidatorResult.success();
  }

  private static Instant issuedAt(Jwt token) {
    Object millis = token.getClaim(ISSUED_AT_MILLIS);
    if (millis instanceof Number number) {
      return Instant.ofEpochMilli(number.longValue());
    }
    return token.getIssuedAt() == null ? Instant.EPOCH : token.getIssuedAt();
  }

  private static OAuth2TokenValidatorResult failure(String description) {
    return OAuth2TokenValidatorResult.failure(
        new OAuth2Error(OAuth2ErrorCodes.INVALID_TOKEN, description, null));
  }
}
