package com.crowded.core.user.api.dto;

import java.util.UUID;

/** {@code GET /me}: who is signed in. */
public record AccountResponse(
    UUID id, String email, String name, String role, UUID organizationId) {}
