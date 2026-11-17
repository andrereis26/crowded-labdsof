package com.crowded.core.event.application.service;

import com.crowded.core.event.application.dto.EventChangeView;
import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.shared.domain.exception.NotFoundException;
import java.util.UUID;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds an event's page from the database and keeps it in the cache until {@link
 * com.crowded.core.event.domain.repository.EventUpdates} announces a change to it. It checks no
 * access: its callers do.
 */
@Service
public class EventPages {

  /** Cache of the pages, by event id. */
  public static final String CACHE = "event-pages";

  private final EventRepository events;
  private final EventChangeRepository changes;
  private final OrganizationRepository organizations;

  EventPages(
      EventRepository events, EventChangeRepository changes, OrganizationRepository organizations) {
    this.events = events;
    this.changes = changes;
    this.organizations = organizations;
  }

  /**
   * The event's page as it is now.
   *
   * @throws NotFoundException when the event does not exist
   */
  @Cacheable(cacheNames = CACHE, key = "#eventId")
  @Transactional(readOnly = true)
  public EventPage current(UUID eventId) {
    Event event =
        events
            .findById(eventId)
            .orElseThrow(() -> new NotFoundException("EVENT_NOT_FOUND", "There is no such event."));
    Organization organization =
        organizations
            .findById(event.getOrganizationId())
            .orElseThrow(() -> new IllegalStateException("Event without its organization"));
    EventState state = event.getState();
    Coordinates coordinates = state.venue().coordinates();
    return new EventPage(
        event.getId(),
        event.getProfile().name(),
        event.getProfile().description(),
        event.getAdmission().type(),
        state.status(),
        state.schedule().start(),
        state.schedule().doorsOpenAt(),
        state.schedule().end(),
        state.venue().name(),
        state.venue().address(),
        event.getDistrict().name(),
        coordinates == null ? null : coordinates.latitude(),
        coordinates == null ? null : coordinates.longitude(),
        organization.getId(),
        organization.getName(),
        changes.findLatest(eventId).map(EventChangeView::of).orElse(null),
        event.getCrowd());
  }
}
