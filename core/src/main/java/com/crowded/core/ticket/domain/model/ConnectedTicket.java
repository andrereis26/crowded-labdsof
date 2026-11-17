package com.crowded.core.ticket.domain.model;

import com.crowded.core.connection.domain.model.EventConnection;
import com.crowded.core.event.domain.model.Event;

/**
 * The connection a ticket gave its owner and the registered event it opens.
 *
 * @param ticket the owner's ticket for the connection; null when they were connected to the event
 *     already without a ticket, such as with its access code
 * @param created false when the owner was connected already, so nothing was added
 */
public record ConnectedTicket(
    EventConnection connection, Event event, Ticket ticket, boolean created) {}
