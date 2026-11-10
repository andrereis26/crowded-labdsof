package com.crowded.core.event.domain.model;

/**
 * An official change as the organization asks for it (FR-016, FR-075).
 *
 * @param newValues the changed fields; null or {@link NewValues#NONE} for a cancellation
 * @param ticketStillValid for a postponement or cancellation, whether tickets stay valid
 * @param refundInfo for a postponement or cancellation, where to ask for a refund
 * @param message the organization's message to the participants
 */
public record RequestedChange(
    ChangeType type,
    NewValues newValues,
    Boolean ticketStillValid,
    String refundInfo,
    String message) {}
