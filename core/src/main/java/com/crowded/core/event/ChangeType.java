package com.crowded.core.event;

/** Kind of an official change, as the {@code change_type} enum type of the database. */
public enum ChangeType {
  CONFIRMATION,
  CANCELLATION,
  POSTPONEMENT,
  TIME_CHANGE,
  VENUE_CHANGE,
  DETAILS_UPDATE
}
