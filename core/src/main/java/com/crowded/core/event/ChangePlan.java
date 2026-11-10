package com.crowded.core.event;

/**
 * A change that differs from the event: what it is, how severe, and the event's state after it.
 *
 * @param before the event's state before the change
 * @param after the event's state after the change
 */
record ChangePlan(
    ChangeType type,
    Severity severity,
    EventState before,
    EventState after,
    Boolean ticketStillValid,
    String refundInfo,
    String message) {}
