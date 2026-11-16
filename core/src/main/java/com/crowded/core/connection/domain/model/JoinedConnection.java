package com.crowded.core.connection.domain.model;

import com.crowded.core.event.domain.model.Event;

/**
 * A participant's connection to a public event and the event it opens.
 *
 * @param created false when the participant was already connected, so nothing was added
 */
public record JoinedConnection(EventConnection connection, Event event, boolean created) {}
