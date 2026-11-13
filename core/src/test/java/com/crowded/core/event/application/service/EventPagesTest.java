package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventType;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.RequestedChange;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.StoredEvents;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.repository.EventChangeRepository;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.organization.domain.model.Organization;
import com.crowded.core.organization.domain.repository.OrganizationRepository;
import com.crowded.core.shared.domain.exception.NotFoundException;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class EventPagesTest {

  private static final Instant NOW = Instant.parse("2026-11-10T10:00:00Z");
  private static final Instant START = Instant.parse("2026-11-20T20:00:00Z");
  private static final Organization RIBEIRA =
      Organization.restore(UUID.randomUUID(), "Ribeira Live", UUID.randomUUID(), NOW);

  private final EventRepository events = mock(EventRepository.class);
  private final EventChangeRepository changes = mock(EventChangeRepository.class);
  private final OrganizationRepository organizations = mock(OrganizationRepository.class);
  private final EventPages pages = new EventPages(events, changes, organizations);
  private Event event;

  @BeforeEach
  void givenAnEventOfItsOrganization() {
    event =
        StoredEvents.restore(
            UUID.randomUUID(),
            RIBEIRA.getId(),
            new EventState(
                EventStatus.SCHEDULED,
                new Schedule(START, START.minusSeconds(3600), null),
                new Venue("Coliseu", "Rua de Passos Manuel 137", new Coordinates(41.146, -8.606))));
    when(events.findById(event.getId())).thenReturn(Optional.of(event));
    when(organizations.findById(RIBEIRA.getId())).thenReturn(Optional.of(RIBEIRA));
  }

  @Test
  void buildsThePageFromTheEventItsOrganizationAndItsLatestChange() {
    EventChange cancellation =
        event
            .publishChange(
                new RequestedChange(ChangeType.CANCELLATION, null, false, "No site", null),
                Provenance.organization(RIBEIRA.getName(), NOW),
                NOW)
            .orElseThrow();
    when(changes.findLatest(event.getId())).thenReturn(Optional.of(cancellation));

    EventPage page = pages.current(event.getId());

    assertThat(page.id()).isEqualTo(event.getId());
    assertThat(page.name()).isEqualTo("Concerto");
    assertThat(page.type()).isEqualTo(EventType.TICKETED);
    assertThat(page.status()).isEqualTo(EventStatus.CANCELLED);
    assertThat(page.startDateTime()).isEqualTo(START);
    assertThat(page.doorsOpenAt()).isEqualTo(START.minusSeconds(3600));
    assertThat(page.endDateTime()).isNull();
    assertThat(page.venueName()).isEqualTo("Coliseu");
    assertThat(page.district()).isEqualTo("Porto");
    assertThat(page.latitude()).isEqualTo(41.146);
    assertThat(page.organizationName()).isEqualTo("Ribeira Live");
    assertThat(page.latestChange().id()).isEqualTo(cancellation.getId());
    assertThat(page.latestChange().provenance().sourceName()).isEqualTo("Ribeira Live");
    assertThat(page.latestChange().newValue()).containsEntry("status", "CANCELLED");
  }

  @Test
  void hasNoLatestChangeWhenTheEventHasNone() {
    when(changes.findLatest(event.getId())).thenReturn(Optional.empty());

    assertThat(pages.current(event.getId()).latestChange()).isNull();
  }

  @Test
  void refusesAnEventThatDoesNotExist() {
    assertThatThrownBy(() -> pages.current(UUID.randomUUID()))
        .isInstanceOfSatisfying(
            NotFoundException.class, e -> assertThat(e.getCode()).isEqualTo("EVENT_NOT_FOUND"));
  }
}
