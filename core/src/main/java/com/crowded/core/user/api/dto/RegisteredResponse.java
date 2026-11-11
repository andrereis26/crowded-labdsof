package com.crowded.core.user.api.dto;

import java.util.UUID;

/** The account {@code POST /auth/register} created. */
public record RegisteredResponse(UUID userId, String role) {}
