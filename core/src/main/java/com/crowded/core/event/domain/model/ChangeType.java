package com.crowded.core.event.domain.model;

/** Kind of an official change. */
public enum ChangeType {
  CONFIRMATION,
  CANCELLATION,
  POSTPONEMENT,
  TIME_CHANGE,
  VENUE_CHANGE,
  DETAILS_UPDATE
}
