package com.crowded.core.ticket.application.dto;

import com.crowded.core.ticket.domain.model.TicketExtraction;

/** What an AI ticket extraction found and how it ended. */
public record ExtractionResult(TicketExtraction extraction, ExtractionOutcome outcome) {

  /** Nothing usable came back from the provider. */
  public static ExtractionResult failed(ExtractionOutcome outcome) {
    return new ExtractionResult(TicketExtraction.NOTHING, outcome);
  }

  /** The provider's answer followed the schema. */
  public static ExtractionResult of(TicketExtraction extraction) {
    return new ExtractionResult(
        extraction,
        extraction.hasEventCode() ? ExtractionOutcome.EXTRACTED : ExtractionOutcome.NO_EVENT_CODE);
  }
}
