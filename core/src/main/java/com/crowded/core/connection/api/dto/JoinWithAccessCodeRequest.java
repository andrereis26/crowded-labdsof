package com.crowded.core.connection.api.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Body of {@code POST /connections} (API Outline, "Connect to an event").
 *
 * @param accessCode typed by the participant or read from the event's QR code
 */
public record JoinWithAccessCodeRequest(@NotBlank String accessCode) {}
