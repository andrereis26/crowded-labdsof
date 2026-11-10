package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.RequestedChange;
import java.util.UUID;

/**
 * Publish {@code change} to the event on behalf of {@code userId}.
 *
 * @param userId the signed-in user, from the token's subject
 */
public record PublishChangeCommand(UUID eventId, UUID userId, RequestedChange change) {}
