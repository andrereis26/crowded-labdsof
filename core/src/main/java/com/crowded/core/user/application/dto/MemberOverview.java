package com.crowded.core.user.application.dto;

import java.time.Instant;
import java.util.UUID;

/** A member as the administration page lists them, without credentials. */
public record MemberOverview(
    UUID userId,
    UUID organizationId,
    String name,
    String email,
    boolean active,
    boolean mustChangePassword,
    Instant temporaryPasswordExpiresAt,
    Instant lastSignedInAt) {}
