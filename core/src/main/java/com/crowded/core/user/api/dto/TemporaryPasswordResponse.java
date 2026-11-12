package com.crowded.core.user.api.dto;

import java.time.Instant;
import java.util.UUID;

/** A new member's temporary password, shown once (SR-022). */
public record TemporaryPasswordResponse(UUID userId, String temporaryPassword, Instant expiresAt) {}
