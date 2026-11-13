package com.crowded.core.ticket.domain.repository;

/**
 * The AI provider that finds the event code and the ticket's details in a ticket's text (ADR 0010).
 * Its answer is untrusted; any exception means it is unavailable.
 */
public interface TicketExtractor {

  /** The provider's raw answer for the ticket's text, which has no personal data. */
  String extract(String textWithoutPersonalData);
}
