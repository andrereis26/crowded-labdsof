package com.crowded.jobs.weather.infrastructure.persistence.repository;

import com.crowded.jobs.weather.domain.repository.EventDistricts;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link EventDistricts} on Core's {@code event} in PostgreSQL. */
@Repository
class EventDistrictsAdapter implements EventDistricts {

  private final SpringDataEventRepository events;

  EventDistrictsAdapter(SpringDataEventRepository events) {
    this.events = events;
  }

  @Override
  @Transactional(readOnly = true)
  public Set<String> withEventsBetween(Instant now, Instant until) {
    return new LinkedHashSet<>(events.findDistrictsBetween(now, until));
  }
}
