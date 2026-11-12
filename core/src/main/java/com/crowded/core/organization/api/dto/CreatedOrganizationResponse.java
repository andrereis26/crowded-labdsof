package com.crowded.core.organization.api.dto;

import java.util.UUID;

/** The organization {@code POST /admin/organizations} created. */
public record CreatedOrganizationResponse(UUID organizationId) {}
