package com.crowded.core.ticket.domain.model;

/** How the ticket found its event: the code the AI extracted, or the one the participant typed. */
public enum MatchMethod {
  EXTRACTED_CODE,
  TYPED_CODE
}
