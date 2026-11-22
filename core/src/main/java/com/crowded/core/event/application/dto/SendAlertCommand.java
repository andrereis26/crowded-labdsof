package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.AlertType;
import java.util.UUID;

/**
 * Send an operational alert of {@code type} to the participants connected to the event, on behalf
 * of {@code userId}.
 *
 * @param userId the signed-in user, from the token's subject
 * @param message the organization's text, as written
 */
public record SendAlertCommand(UUID eventId, UUID userId, AlertType type, String message) {}
