package com.crowded.core.user.api.dto;

import java.time.Instant;
import java.util.UUID;

/** A successful {@code POST /auth/login} (API Outline). */
public record SignInResponse(
    String accessToken, Instant expiresAt, boolean mustChangePassword, SignedInUser user) {

  /** Who signed in. */
  public record SignedInUser(UUID id, String role, UUID organizationId) {}
}
