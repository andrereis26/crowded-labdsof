package com.crowded.core.user.domain.model;

import java.time.Instant;

/** A signed access token and when it expires. */
public record IssuedToken(String value, Instant expiresAt) {}
