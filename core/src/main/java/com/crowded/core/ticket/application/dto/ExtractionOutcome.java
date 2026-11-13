package com.crowded.core.ticket.application.dto;

/** How an AI ticket extraction ended, for the logs and metrics. */
public enum ExtractionOutcome {
  /** The answer followed the schema and has an event code. */
  EXTRACTED,
  /** The answer followed the schema but has no event code. */
  NO_EVENT_CODE,
  /** The answer did not follow the schema and was discarded. */
  INVALID_OUTPUT,
  /** The provider took longer than the timeout. */
  TIMEOUT,
  /** The provider is unavailable or failed. */
  UNAVAILABLE
}
