package com.crowded.core.user.application.dto;

import com.crowded.core.user.domain.model.Role;
import java.util.UUID;

/** The signed-in user, as the apps show it. The name is null for a participant. */
public record AccountOverview(UUID id, String email, String name, Role role, UUID organizationId) {}
