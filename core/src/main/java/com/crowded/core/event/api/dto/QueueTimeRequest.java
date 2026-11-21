package com.crowded.core.event.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * A waiting time in {@code PUT /events/{eventId}/queues} (API Outline, journey 5).
 *
 * @param pointName one of the event's entrances or queue points
 * @param waitMinutes whole minutes, 0 or more
 */
public record QueueTimeRequest(
    @NotBlank String pointName, @NotNull @PositiveOrZero Integer waitMinutes) {}
