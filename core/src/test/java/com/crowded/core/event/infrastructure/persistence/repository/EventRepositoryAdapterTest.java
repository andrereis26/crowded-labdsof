package com.crowded.core.event.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.Coordinates;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.NewValues;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.RequestedChange;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.infrastructure.persistence.entity.EventEntity;
import com.crowded.core.event.infrastructure.persistence.mapper.EventPersistenceMapperImpl;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class EventRepositoryAdapterTest {

  private final SpringDataEventRepository rows = mock(SpringDataEventRepository.class);
  private final EventRepositoryAdapter events =
      new EventRepositoryAdapter(rows, new EventPersistenceMapperImpl());

  @Test
  void readsTheLockedRowAsAnEvent() {
    EventEntity row = row();
    when(rows.findForUpdateById(row.getId())).thenReturn(Optional.of(row));

    Event event = events.findByIdForUpdate(row.getId()).orElseThrow();

    assertThat(event.getId()).isEqualTo(row.getId());
    assertThat(event.getOrganizationId()).isEqualTo(row.getOrganizationId());
    assertThat(event.getState())
        .isEqualTo(
            new EventState(
                EventStatus.SCHEDULED,
                new Schedule(row.getStartDateTime(), row.getDoorsOpenAt(), row.getEndDateTime()),
                new Venue("Coliseu", "Porto", new Coordinates(41.1, -8.6))));
  }

  @Test
  void writesTheNewStateToTheSameRow() {
    EventEntity row = row();
    UUID organization = row.getOrganizationId();
    when(rows.findForUpdateById(row.getId())).thenReturn(Optional.of(row));
    when(rows.findById(row.getId())).thenReturn(Optional.of(row));
    Event event = events.findByIdForUpdate(row.getId()).orElseThrow();
    event.publishChange(
        new RequestedChange(
            ChangeType.VENUE_CHANGE,
            new NewValues(null, null, null, "Hard Club", "Mercado Ferreira Borges", null, null),
            null,
            null,
            null),
        Provenance.organization("Ribeira Live", Instant.parse("2026-11-10T10:00:00Z")),
        Instant.parse("2026-11-10T10:00:00Z"));

    events.save(event);

    assertThat(row.getStatus()).isEqualTo(EventStatus.RELOCATED);
    assertThat(row.getVenueName()).isEqualTo("Hard Club");
    assertThat(row.getAddress()).isEqualTo("Mercado Ferreira Borges");
    assertThat(row.getLatitude()).isNull();
    assertThat(row.getOrganizationId()).isEqualTo(organization);
  }

  @Test
  void failsWhenTheRowIsGone() {
    Event event = new EventPersistenceMapperImpl().toDomain(row());

    assertThatThrownBy(() -> events.save(event)).isInstanceOf(IllegalStateException.class);
  }

  private static EventEntity row() {
    EventEntity row = new EventEntity();
    row.setId(UUID.randomUUID());
    row.setOrganizationId(UUID.randomUUID());
    row.setStatus(EventStatus.SCHEDULED);
    row.setStartDateTime(Instant.parse("2026-11-20T20:00:00Z"));
    row.setDoorsOpenAt(Instant.parse("2026-11-20T18:30:00Z"));
    row.setEndDateTime(Instant.parse("2026-11-20T23:00:00Z"));
    row.setVenueName("Coliseu");
    row.setAddress("Porto");
    row.setLatitude(41.1);
    row.setLongitude(-8.6);
    return row;
  }
}
