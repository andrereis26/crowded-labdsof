package com.crowded.core.user.domain.model;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.random.RandomGenerator;

/**
 * A refresh token as the client holds it: the session it continues and a 256-bit secret (ADR 0021).
 * Core stores only the secret's hash, so the stored sessions cannot be replayed.
 */
public record RefreshToken(UUID sessionId, String secret) {

  private static final int SECRET_BYTES = 32;
  private static final char SEPARATOR = '.';

  public RefreshToken {
    Objects.requireNonNull(sessionId, "sessionId");
    if (secret == null || secret.isBlank()) {
      throw new IllegalArgumentException("A refresh token needs a secret");
    }
  }

  /** A new token for the session {@code sessionId}. */
  public static RefreshToken generate(UUID sessionId, RandomGenerator random) {
    byte[] bytes = new byte[SECRET_BYTES];
    random.nextBytes(bytes);
    return new RefreshToken(
        sessionId, Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
  }

  /** The token a client sent, or empty when it is not one Core issued. */
  public static Optional<RefreshToken> parse(String value) {
    if (value == null) {
      return Optional.empty();
    }
    int separator = value.indexOf(SEPARATOR);
    if (separator < 0 || separator == value.length() - 1) {
      return Optional.empty();
    }
    try {
      UUID sessionId = UUID.fromString(value.substring(0, separator));
      return Optional.of(new RefreshToken(sessionId, value.substring(separator + 1)));
    } catch (IllegalArgumentException e) {
      return Optional.empty();
    }
  }

  /** The token as the client sends it back. */
  public String value() {
    return sessionId + String.valueOf(SEPARATOR) + secret;
  }

  /** The secret's SHA-256, which is all a session keeps of it. */
  public String hash() {
    try {
      return HexFormat.of()
          .formatHex(
              MessageDigest.getInstance("SHA-256").digest(secret.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 is always available", e);
    }
  }

  /** Without the secret, so a log never holds a usable token. */
  @Override
  public String toString() {
    return "RefreshToken[" + sessionId + "]";
  }
}
