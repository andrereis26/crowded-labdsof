package com.crowded.core.user.application.dto;

import com.crowded.core.user.domain.model.IssuedToken;
import com.crowded.core.user.domain.model.User;
import java.time.Instant;

/**
 * A started or continued session: the access token, the refresh token that continues it until
 * {@code refreshExpiresAt}, and who it is for.
 */
public record SignedIn(
    IssuedToken token,
    String refreshToken,
    Instant refreshExpiresAt,
    User user,
    boolean mustChangePassword) {}
