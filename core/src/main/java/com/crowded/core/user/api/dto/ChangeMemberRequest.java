package com.crowded.core.user.api.dto;

/**
 * {@code PATCH /admin/members/{userId}}.
 *
 * @param active the new state, or null to keep it
 * @param resetPassword true to issue a new temporary password; null or false otherwise
 */
public record ChangeMemberRequest(Boolean active, Boolean resetPassword) {}
