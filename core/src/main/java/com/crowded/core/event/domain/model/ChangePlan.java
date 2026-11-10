package com.crowded.core.event.domain.model;

/**
 * A change that differs from the event: what it is, how severe, and the event's state before and
 * after it.
 */
public record ChangePlan(
    ChangeType type,
    Severity severity,
    EventState before,
    EventState after,
    Boolean ticketStillValid,
    String refundInfo,
    String message) {}
