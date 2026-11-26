package com.crowded.jobs.weather.domain.repository;

import java.time.Instant;
import java.util.Set;

/** The districts that have events, read from Core's events. */
public interface EventDistricts {

  /**
   * The districts of the events, neither cancelled nor ended, that end after {@code now} and start
   * before {@code until}.
   */
  Set<String> withEventsBetween(Instant now, Instant until);
}
