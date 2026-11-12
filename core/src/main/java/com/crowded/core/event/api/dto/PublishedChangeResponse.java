package com.crowded.core.event.api.dto;

import java.util.UUID;

/**
 * Response of {@code POST /events/{eventId}/changes}.
 *
 * @param severity {@code CRITICAL}, {@code WARNING} or {@code INFO}
 */
public record PublishedChangeResponse(UUID changeId, String severity) {}
