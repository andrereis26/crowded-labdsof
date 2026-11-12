package com.crowded.core.user.infrastructure.config;

import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.user.domain.repository.TokenRevocations;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Refuses a token issued before its user's tokens were revoked (ADR 0004), such as when an
 * administrator deactivates a member. When the revocations cannot be read, the token is refused
 * too: the services fail safe.
 */
class RevokedTokenValidator implements OAuth2TokenValidator<Jwt> {

  private final TokenRevocations revocations;

  RevokedTokenValidator(TokenRevocations revocations) {
    this.revocations = revocations;
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
    Optional<Instant> revokedBefore;
    try {
      revokedBefore = revocations.revokedBefore(userId);
    } catch (UnavailableException e) {
      return failure("The token's status cannot be checked.");
    }
    if (revokedBefore.isPresent() && issuedAt(token).isBefore(revokedBefore.get())) {
      return failure("The token was revoked.");
    }
    return OAuth2TokenValidatorResult.success();
  }

  /** {@code iat_ms} when Core issued the token, else {@code iat}, which has whole seconds. */
  private static Instant issuedAt(Jwt token) {
    Object millis = token.getClaim(JwtTokenIssuer.ISSUED_AT_MILLIS);
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
