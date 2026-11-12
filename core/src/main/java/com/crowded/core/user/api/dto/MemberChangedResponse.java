package com.crowded.core.user.api.dto;

import java.time.Instant;
import java.util.UUID;

/** A member after {@code PATCH /admin/members/{userId}}; the temporary password is shown once. */
public record MemberChangedResponse(
    UUID userId, boolean active, String temporaryPassword, Instant expiresAt) {}
