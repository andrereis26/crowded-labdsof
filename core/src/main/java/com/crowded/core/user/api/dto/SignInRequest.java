package com.crowded.core.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** {@code POST /auth/login}. Without {@code delivery}, the tokens come in the body. */
public record SignInRequest(
    @NotBlank @Size(max = 254) String email, @NotNull String password, TokenDelivery delivery) {}
