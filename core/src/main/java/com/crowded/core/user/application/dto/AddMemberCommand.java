package com.crowded.core.user.application.dto;

import java.util.UUID;

/** An administrator adds a member to an organization. */
public record AddMemberCommand(
    UUID administratorId, UUID organizationId, String name, String email) {}
