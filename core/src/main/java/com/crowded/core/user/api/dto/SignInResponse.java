package com.crowded.core.user.api.dto;

import java.time.Instant;

/** A successful {@code POST /auth/login} (API Outline). */
public record SignInResponse(
    String accessToken, Instant expiresAt, boolean mustChangePassword, SignedInUserResponse user) {}
