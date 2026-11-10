package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.ChangeType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /events/{eventId}/changes} (API Outline, journey 3).
 *
 * @param type {@code POSTPONEMENT}, {@code TIME_CHANGE}, {@code VENUE_CHANGE} or {@code
 *     CANCELLATION}
 * @param newValue the changed fields; none for a cancellation
 * @param ticketStillValid for a postponement or cancellation, whether tickets stay valid
 * @param refundInfo for a postponement or cancellation, where to ask for a refund
 * @param message the organization's message to the participants
 */
public record PublishChangeRequest(
    @NotNull ChangeType type,
    @Valid NewValueRequest newValue,
    Boolean ticketStillValid,
    @Size(max = 2000) String refundInfo,
    @Size(max = 2000) String message) {}
