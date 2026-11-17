package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.CrowdLevel;
import jakarta.validation.constraints.NotNull;

/**
 * Body of {@code PUT /events/{eventId}/crowd} (API Outline, journey 5).
 *
 * @param level {@code LOW}, {@code MODERATE}, {@code HIGH} or {@code FULL}
 */
public record PublishCrowdLevelRequest(@NotNull CrowdLevel level) {}
