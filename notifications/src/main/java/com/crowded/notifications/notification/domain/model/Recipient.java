package com.crowded.notifications.notification.domain.model;

import java.util.UUID;

/**
 * A participant to notify.
 *
 * @param ticket whether they are connected through a ticket, so the ticket's validity and the
 *     refund apply to them
 */
public record Recipient(UUID userId, boolean ticket) {}
