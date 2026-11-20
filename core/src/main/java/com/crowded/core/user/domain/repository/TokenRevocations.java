package com.crowded.core.user.domain.repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Tokens revoked before they expire (ADR 0004, ADR 0021): every token of a user issued before a
 * given time, such as when an administrator deactivates them or gives them a new temporary
 * password, and single tokens, when their user signs out.
 */
public interface TokenRevocations {

  /**
   * Revokes every token of {@code userId} issued before {@code at}, until they would have expired.
   *
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when the revocation
   *     cannot be stored
   */
  void revokeIssuedBefore(UUID userId, Instant at);

  /**
   * The time before which {@code userId}'s tokens are revoked, if any.
   *
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when it cannot be read
   */
  Optional<Instant> revokedBefore(UUID userId);

  /**
   * Revokes the access token {@code tokenId} ({@code jti}) until it expires at {@code expiresAt}.
   *
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when the revocation
   *     cannot be stored
   */
  void revokeToken(String tokenId, Instant expiresAt);

  /**
   * Whether the access token {@code tokenId} was revoked.
   *
   * @throws com.crowded.core.shared.domain.exception.UnavailableException when it cannot be read
   */
  boolean isTokenRevoked(String tokenId);
}
