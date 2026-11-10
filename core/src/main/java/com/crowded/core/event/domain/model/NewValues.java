package com.crowded.core.event.domain.model;

import java.time.Instant;

/** The fields an official change sets; the ones left null keep their value or move with it. */
public record NewValues(
    Instant startDateTime,
    Instant doorsOpenAt,
    Instant endDateTime,
    String venueName,
    String address,
    Double latitude,
    Double longitude) {

  /** No fields, as for a cancellation. */
  public static final NewValues NONE = new NewValues(null, null, null, null, null, null, null);
}
