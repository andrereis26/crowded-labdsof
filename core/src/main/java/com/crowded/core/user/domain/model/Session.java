package com.crowded.core.user.domain.model;

import com.crowded.core.shared.domain.exception.NotAuthenticatedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * A signed-in session, continued with a refresh token that changes on each use (ADR 0021). Using a
 * replaced token again means someone else holds it, so the session ends; within {@link
 * #ROTATION_GRACE} of the change it is only refused, because another tab refreshed at the same
 * time.
 */
public final class Session {

  /** How long a replaced token is refused without ending the session. */
  public static final Duration ROTATION_GRACE = Duration.ofSeconds(10);

  private final UUID id;
  private final UUID userId;
  private final Instant startedAt;
  private final Instant endsAt;
  private final Duration idleLifetime;
  private String tokenHash;
  private String previousTokenHash;
  private Instant rotatedAt;
  private Instant lastUsedAt;
  private boolean ended;

  private Session(
      UUID id,
      UUID userId,
      Instant startedAt,
      Instant endsAt,
      Duration idleLifetime,
      String tokenHash,
      String previousTokenHash,
      Instant rotatedAt,
      Instant lastUsedAt) {
    this.id = Objects.requireNonNull(id, "id");
    this.userId = Objects.requireNonNull(userId, "userId");
    this.startedAt = Objects.requireNonNull(startedAt, "startedAt");
    this.endsAt = Objects.requireNonNull(endsAt, "endsAt");
    this.idleLifetime = Objects.requireNonNull(idleLifetime, "idleLifetime");
    this.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
    this.previousTokenHash = previousTokenHash;
    this.rotatedAt = rotatedAt;
    this.lastUsedAt = Objects.requireNonNull(lastUsedAt, "lastUsedAt");
  }

  /** Starts the session {@code token} names, for {@code userId}, at sign-in. */
  public static Session start(
      RefreshToken token, UUID userId, SessionLifetime lifetime, Instant now) {
    Objects.requireNonNull(lifetime, "lifetime");
    return new Session(
        token.sessionId(),
        userId,
        now,
        now.plus(lifetime.maximum()),
        lifetime.idle(),
        token.hash(),
        null,
        null,
        now);
  }

  /** A stored session. */
  public static Session restore(
      UUID id,
      UUID userId,
      Instant startedAt,
      Instant endsAt,
      Duration idleLifetime,
      String tokenHash,
      String previousTokenHash,
      Instant rotatedAt,
      Instant lastUsedAt) {
    return new Session(
        id,
        userId,
        startedAt,
        endsAt,
        idleLifetime,
        tokenHash,
        previousTokenHash,
        rotatedAt,
        lastUsedAt);
  }

  /** The refusal of a refresh token that does not continue a session. */
  public static NotAuthenticatedException invalidToken() {
    return new NotAuthenticatedException(
        "INVALID_REFRESH_TOKEN", "The session ended. Sign in again.");
  }

  /**
   * Continues the session: {@code presented} is spent and {@code next} replaces it.
   *
   * @throws NotAuthenticatedException with {@code REFRESH_TOKEN_ROTATED} when {@code presented} was
   *     replaced less than {@link #ROTATION_GRACE} ago, and {@code INVALID_REFRESH_TOKEN} when the
   *     session expired or {@code presented} is not its token; a replaced token used later ends the
   *     session
   */
  public void refresh(RefreshToken presented, RefreshToken next, Instant now) {
    if (ended || !now.isBefore(expiresAt())) {
      ended = true;
      throw invalidToken();
    }
    String presentedHash = presented.hash();
    if (!presentedHash.equals(tokenHash)) {
      if (presentedHash.equals(previousTokenHash) && now.isBefore(rotatedAt.plus(ROTATION_GRACE))) {
        throw new NotAuthenticatedException(
            "REFRESH_TOKEN_ROTATED", "The session was just refreshed. Repeat the request.");
      }
      if (presentedHash.equals(previousTokenHash)) {
        ended = true;
      }
      throw invalidToken();
    }
    previousTokenHash = tokenHash;
    tokenHash = next.hash();
    rotatedAt = now;
    lastUsedAt = now;
  }

  /**
   * Ends the session when the user signs out with one of its tokens.
   *
   * @return false when {@code presented} is neither its token nor the one it replaced
   */
  public boolean end(RefreshToken presented) {
    String presentedHash = presented.hash();
    if (presentedHash.equals(tokenHash) || presentedHash.equals(previousTokenHash)) {
      ended = true;
    }
    return ended;
  }

  /** When the session ends unless it is used again, and at the latest its maximum age. */
  public Instant expiresAt() {
    Instant idleEnd = lastUsedAt.plus(idleLifetime);
    return idleEnd.isBefore(endsAt) ? idleEnd : endsAt;
  }

  /** Whether the session ended: signed out, expired, or its token used twice. */
  public boolean hasEnded() {
    return ended;
  }

  public UUID getId() {
    return id;
  }

  public UUID getUserId() {
    return userId;
  }

  public Instant getStartedAt() {
    return startedAt;
  }

  public Instant getEndsAt() {
    return endsAt;
  }

  public Duration getIdleLifetime() {
    return idleLifetime;
  }

  public String getTokenHash() {
    return tokenHash;
  }

  public String getPreviousTokenHash() {
    return previousTokenHash;
  }

  public Instant getRotatedAt() {
    return rotatedAt;
  }

  public Instant getLastUsedAt() {
    return lastUsedAt;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof Session session && id.equals(session.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "Session[" + id + ", user " + userId + "]";
  }
}
