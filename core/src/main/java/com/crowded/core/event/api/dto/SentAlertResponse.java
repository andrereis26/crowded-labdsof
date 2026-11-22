package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.AlertType;
import com.crowded.core.event.domain.model.Severity;
import java.time.Instant;
import java.util.UUID;

/**
 * Response of {@code POST /events/{eventId}/alerts}: the alert as participants see it.
 *
 * @param recipientsCount how many participants were connected to the event when it was sent
 */
public record SentAlertResponse(
    UUID id,
    AlertType type,
    Severity severity,
    String message,
    Instant sentAt,
    int recipientsCount) {}
