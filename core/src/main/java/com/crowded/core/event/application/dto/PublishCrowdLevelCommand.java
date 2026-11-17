package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.CrowdLevel;
import java.util.UUID;

/**
 * Publish {@code level} as the event's crowd level on behalf of {@code userId}.
 *
 * @param userId the signed-in user, from the token's subject
 */
public record PublishCrowdLevelCommand(UUID eventId, UUID userId, CrowdLevel level) {}
