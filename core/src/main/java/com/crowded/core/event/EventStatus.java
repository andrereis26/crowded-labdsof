package com.crowded.core.event;

/** Status of an event, as the {@code event_status} enum type of the database. */
public enum EventStatus {
  PROPOSED,
  SCHEDULED,
  POSTPONED,
  RELOCATED,
  CANCELLED,
  ONGOING,
  ENDED
}
