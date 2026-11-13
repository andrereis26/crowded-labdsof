package com.crowded.core.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /auth/password}. */
public record ChoosePasswordRequest(
    @NotBlank @Size(max = 254) String email,
    @NotNull String temporaryPassword,
    @NotNull String newPassword) {}
