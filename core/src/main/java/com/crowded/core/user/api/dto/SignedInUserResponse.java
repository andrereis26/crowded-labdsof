package com.crowded.core.user.api.dto;

import java.util.UUID;

/** Who signed in, in a {@link SignInResponse}. */
public record SignedInUserResponse(UUID id, String role, UUID organizationId) {}
