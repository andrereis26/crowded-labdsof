package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.Event;
import java.time.Instant;

/**
 * An event as its organization sees it in the backoffice.
 *
 * @param connectedUsers how many participants are connected to it now
 * @param imageUpdatedAt when its image was uploaded; null when it has none
 */
public record ManagedEvent(Event event, long connectedUsers, Instant imageUpdatedAt) {}
