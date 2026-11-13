package com.crowded.core.event.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.SourceType;
import com.crowded.core.event.infrastructure.persistence.entity.EventChangeEntity;
import com.crowded.core.event.infrastructure.persistence.mapper.EventChangePersistenceMapperImpl;
import com.crowded.core.shared.infrastructure.persistence.JsonColumnConverter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class EventChangeRepositoryAdapterTest {

  private final SpringDataEventChangeRepository rows = mock(SpringDataEventChangeRepository.class);
  private final JsonColumnConverter json = new JsonColumnConverter(JsonMapper.builder().build());
  private final EventChangeRepositoryAdapter changes =
      new EventChangeRepositoryAdapter(rows, new EventChangePersistenceMapperImpl(json), json);

  @Test
  void storesTheChangedValuesAsJsonAndReadsThemBack() {
    Map<String, Object> newValue = new LinkedHashMap<>();
    newValue.put("venueName", "Hard Club");
    newValue.put("latitude", null);
    EventChange change =
        EventChange.restore(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.VENUE_CHANGE,
            Severity.CRITICAL,
            Map.of("venueName", "Coliseu"),
            newValue,
            null,
            "Reembolso no site",
            "Mudámos de sala.",
            new Provenance(
                SourceType.ORGANIZATION,
                "Ribeira Live",
                Instant.parse("2026-11-10T10:00:00Z"),
                true));

    changes.save(change);

    ArgumentCaptor<EventChangeEntity> saved = ArgumentCaptor.forClass(EventChangeEntity.class);
    verify(rows).save(saved.capture());
    EventChangeEntity row = saved.getValue();
    assertThat(row.getNewValue()).isEqualTo("{\"venueName\":\"Hard Club\",\"latitude\":null}");
    assertThat(row.getPreviousValue()).isEqualTo("{\"venueName\":\"Coliseu\"}");
    assertThat(row.isProvenanceConfirmed()).isTrue();

    when(rows.findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(
            change.getEventId(), ChangeType.VENUE_CHANGE))
        .thenReturn(Optional.of(row));

    assertThat(changes.findLatest(change.getEventId(), ChangeType.VENUE_CHANGE))
        .get()
        .usingRecursiveComparison()
        .isEqualTo(change);
  }

  @Test
  void readsTheLatestChangeAndTheHistoryInTheOrderOfTheRows() {
    UUID event = UUID.randomUUID();
    EventChange older = cancellation(event, Instant.parse("2026-11-09T10:00:00Z"));
    EventChange newer = cancellation(event, Instant.parse("2026-11-10T10:00:00Z"));
    EventChangePersistenceMapperImpl mapper = new EventChangePersistenceMapperImpl(json);
    when(rows.findFirstByEventIdOrderByProvenanceLastUpdatedAtDescIdDesc(event))
        .thenReturn(Optional.of(mapper.toEntity(newer)));
    when(rows.findByEventIdOrderByProvenanceLastUpdatedAtDescIdDesc(event))
        .thenReturn(List.of(mapper.toEntity(newer), mapper.toEntity(older)));

    assertThat(changes.findLatest(event)).get().isEqualTo(newer);
    assertThat(changes.findByEvent(event)).containsExactly(newer, older);
    assertThat(changes.findLatest(UUID.randomUUID())).isEmpty();
    assertThat(changes.findByEvent(UUID.randomUUID())).isEmpty();
  }

  private static EventChange cancellation(UUID event, Instant at) {
    return EventChange.restore(
        UUID.randomUUID(),
        event,
        ChangeType.CANCELLATION,
        Severity.CRITICAL,
        Map.of("status", "SCHEDULED"),
        Map.of("status", "CANCELLED"),
        false,
        null,
        null,
        new Provenance(SourceType.ORGANIZATION, "Ribeira Live", at, true));
  }
}
