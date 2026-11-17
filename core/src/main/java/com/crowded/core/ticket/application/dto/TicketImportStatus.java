package com.crowded.core.ticket.application.dto;

/** How a ticket import ended, as {@code POST /tickets} answers it (API Outline). */
public enum TicketImportStatus {
  /** The ticket matched a registered event and its owner is connected to it. */
  CONNECTED,
  /** The import waits for the event code printed on the ticket. */
  NEEDS_EVENT_CODE
}
