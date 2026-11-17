package com.crowded.core.ticket.application.dto;

import com.crowded.core.ticket.domain.model.TicketDetails;
import java.util.UUID;

/**
 * The result of importing a ticket file, with the fields the API Outline gives it.
 *
 * @param importId the pending import, when the event code is still needed
 * @param ticketId the ticket, when connected; null when the participant was connected to the event
 *     already without one
 * @param connectionId the participant's connection, when connected
 * @param event the registered event, when connected
 * @param ticket gate, sector and seat as read from the ticket
 */
public record TicketImportResult(
    TicketImportStatus status,
    UUID importId,
    UUID ticketId,
    UUID connectionId,
    ImportedEvent event,
    TicketDetails ticket) {

  /** The import waits for the event code. */
  public static TicketImportResult needsEventCode(UUID importId, TicketDetails ticket) {
    return new TicketImportResult(
        TicketImportStatus.NEEDS_EVENT_CODE, importId, null, null, null, ticket);
  }

  /** The ticket matched {@code event} and its owner is connected to it. */
  public static TicketImportResult connected(
      UUID ticketId, UUID connectionId, ImportedEvent event, TicketDetails ticket) {
    return new TicketImportResult(
        TicketImportStatus.CONNECTED, null, ticketId, connectionId, event, ticket);
  }
}
