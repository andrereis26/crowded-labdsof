package com.crowded.core.user.api.dto;

/**
 * {@code POST /auth/refresh} and {@code POST /auth/logout} from the phone app, which sends its
 * refresh token in the body; browsers send the cookie and no body.
 */
public record SessionTokenRequest(String refreshToken) {}
