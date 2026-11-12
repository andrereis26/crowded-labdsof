package com.crowded.core.organization.api.dto;

import java.time.Instant;
import java.util.UUID;

/** A member in {@link OrganizationResponse}, without credentials. */
public record OrganizationMemberResponse(
    UUID userId,
    String name,
    String email,
    boolean active,
    boolean mustChangePassword,
    Instant temporaryPasswordExpiresAt,
    Instant lastSignedInAt) {}
