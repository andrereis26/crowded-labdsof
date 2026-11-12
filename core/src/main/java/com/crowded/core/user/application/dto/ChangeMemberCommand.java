package com.crowded.core.user.application.dto;

import java.util.UUID;

/**
 * An administrator deactivates or reactivates a member, or gives them a new temporary password.
 *
 * @param active the new state, or null to keep it
 * @param resetPassword true to issue a new temporary password; null or false otherwise
 */
public record ChangeMemberCommand(
    UUID administratorId, UUID userId, Boolean active, Boolean resetPassword) {}
