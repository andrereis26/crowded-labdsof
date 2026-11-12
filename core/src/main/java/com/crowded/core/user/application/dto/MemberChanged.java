package com.crowded.core.user.application.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * A member after an administrator changed them.
 *
 * @param temporaryPassword the new temporary password, shown once, or null when none was issued
 */
public record MemberChanged(
    UUID userId, boolean active, String temporaryPassword, Instant expiresAt) {}
