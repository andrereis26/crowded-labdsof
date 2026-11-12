package com.crowded.core.organization.application.dto;

import java.util.UUID;

/** An administrator creates an organization. */
public record CreateOrganizationCommand(UUID administratorId, String name) {}
