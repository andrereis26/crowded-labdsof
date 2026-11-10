package com.crowded.core.event.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.Severity;
import com.crowded.core.event.domain.model.SourceType;
import com.crowded.core.event.infrastructure.persistence.entity.EventChangeEntity;
import com.crowded.core.event.infrastructure.persistence.mapper.EventChangePersistenceMapperImpl;
import com.crowded.core.shared.infrastructure.persistence.JsonColumnConverter;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class EventChangeRepositoryAdapterTest {

  private final SpringDataEventChangeRepository rows = mock(SpringDataEventChangeRepository.class);
  private final EventChangeRepositoryAdapter changes =
      new EventChangeRepositoryAdapter(
          rows,
          new EventChangePersistenceMapperImpl(
              new JsonColumnConverter(JsonMapper.builder().build())));

  @Test
  void storesTheChangedValuesAsJsonAndReadsThemBack() {
    Map<String, Object> newValue = new LinkedHashMap<>();
    newValue.put("venueName", "Hard Club");
    newValue.put("latitude", null);
    EventChange change =
        new EventChange(
            UUID.randomUUID(),
            UUID.randomUUID(),
            ChangeType.VENUE_CHANGE,
            Severity.CRITICAL,
            Map.of("venueName", "Coliseu"),
            newValue,
            null,
            "Reembolso no site",
            "Mudámos de sala.",
            SourceType.ORGANIZATION,
            "Ribeira Live",
            Instant.parse("2026-11-10T10:00:00Z"),
            true);

    changes.save(change);

    ArgumentCaptor<EventChangeEntity> saved = ArgumentCaptor.forClass(EventChangeEntity.class);
    verify(rows).save(saved.capture());
    EventChangeEntity row = saved.getValue();
    assertThat(row.getNewValue()).isEqualTo("{\"venueName\":\"Hard Club\",\"latitude\":null}");
    assertThat(row.getPreviousValue()).isEqualTo("{\"venueName\":\"Coliseu\"}");
    assertThat(row.isProvenanceConfirmed()).isTrue();

    when(rows.findFirstByEventIdAndTypeOrderByProvenanceLastUpdatedAtDesc(
            change.eventId(), ChangeType.VENUE_CHANGE))
        .thenReturn(Optional.of(row));

    assertThat(changes.findLatest(change.eventId(), ChangeType.VENUE_CHANGE)).contains(change);
  }
}
