package com.crowded.core.organization.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /admin/organizations}. */
public record CreateOrganizationRequest(@NotBlank @Size(max = 120) String name) {}
