package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.AlertMessage;
import com.crowded.core.event.domain.model.AlertType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /events/{eventId}/alerts} (API Outline, journey 5).
 *
 * @param type {@code ENTRANCE_CLOSURE}, {@code CAPACITY_REACHED}, {@code SCHEDULE_DELAY} or {@code
 *     OTHER}
 * @param message the organization's text to the participants
 */
public record SendAlertRequest(
    @NotNull AlertType type, @NotBlank @Size(max = AlertMessage.MAX_LENGTH) String message) {}
