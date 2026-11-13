package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.Event;

/**
 * An event as its organization sees it in the backoffice.
 *
 * @param connectedUsers how many participants are connected to it now
 */
public record ManagedEvent(Event event, long connectedUsers) {}
