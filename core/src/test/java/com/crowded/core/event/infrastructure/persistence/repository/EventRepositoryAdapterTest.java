package com.crowded.core.event.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.event.domain.model.ChangePlan;
import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.Severity;
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
                row.getStartDateTime(),
                row.getDoorsOpenAt(),
                row.getEndDateTime(),
                "Coliseu",
                "Porto",
                41.1,
                -8.6));
  }

  @Test
  void writesTheNewStateToTheSameRow() {
    EventEntity row = row();
    UUID organization = row.getOrganizationId();
    when(rows.findForUpdateById(row.getId())).thenReturn(Optional.of(row));
    when(rows.findById(row.getId())).thenReturn(Optional.of(row));
    Event event = events.findByIdForUpdate(row.getId()).orElseThrow();
    EventState relocated =
        new EventState(
            EventStatus.RELOCATED,
            row.getStartDateTime(),
            row.getDoorsOpenAt(),
            row.getEndDateTime(),
            "Hard Club",
            "Mercado Ferreira Borges",
            null,
            null);
    event.apply(
        new ChangePlan(
            ChangeType.VENUE_CHANGE,
            Severity.CRITICAL,
            event.getState(),
            relocated,
            null,
            null,
            null));

    events.save(event);

    assertThat(row.getStatus()).isEqualTo(EventStatus.RELOCATED);
    assertThat(row.getVenueName()).isEqualTo("Hard Club");
    assertThat(row.getAddress()).isEqualTo("Mercado Ferreira Borges");
    assertThat(row.getLatitude()).isNull();
    assertThat(row.getOrganizationId()).isEqualTo(organization);
  }

  @Test
  void failsWhenTheRowIsGone() {
    Event event = new Event(UUID.randomUUID(), null, null);

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
