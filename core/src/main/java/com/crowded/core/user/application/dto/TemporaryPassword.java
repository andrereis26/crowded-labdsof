package com.crowded.core.user.application.dto;

import java.time.Instant;
import java.util.UUID;

/** A temporary password, shown to the administrator once and never stored in clear (SR-022). */
public record TemporaryPassword(UUID userId, String password, Instant expiresAt) {}
