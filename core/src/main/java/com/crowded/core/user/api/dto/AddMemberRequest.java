package com.crowded.core.user.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** {@code POST /admin/organizations/{organizationId}/members}. */
public record AddMemberRequest(
    @NotBlank @Size(max = 120) String name, @NotBlank @Email @Size(max = 254) String email) {}
