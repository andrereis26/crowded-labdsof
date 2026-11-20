package com.crowded.core.user.application.dto;

import java.time.Instant;

/**
 * Signing out: the session's refresh token and, when the request still had a valid one, the access
 * token's id and expiry. Each may be null.
 */
public record SignOutCommand(String refreshToken, String accessTokenId, Instant accessExpiresAt) {}
