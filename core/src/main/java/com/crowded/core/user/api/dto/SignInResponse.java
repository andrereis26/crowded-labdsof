package com.crowded.core.user.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;

/**
 * A started or continued session (API Outline). The tokens are left out when they went into
 * cookies.
 */
public record SignInResponse(
    @JsonInclude(JsonInclude.Include.NON_NULL) String accessToken,
    @JsonInclude(JsonInclude.Include.NON_NULL) String refreshToken,
    Instant expiresAt,
    Instant refreshExpiresAt,
    boolean mustChangePassword,
    SignedInUserResponse user) {}
