package com.crowded.notifications.notification.domain.model;

/** Kind of an official change, as Core records it in {@code change_type}. */
public enum ChangeType {
  CANCELLATION,
  POSTPONEMENT,
  TIME_CHANGE,
  VENUE_CHANGE,
  DETAILS_UPDATE
}
