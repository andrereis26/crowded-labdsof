package com.crowded.core.event.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.ForecastSnapshot;
import com.crowded.core.event.domain.model.IssuedWarning;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.WarningLevel;
import com.crowded.core.event.domain.model.WarningsSnapshot;
import com.crowded.core.event.domain.model.WeatherForecast;
import com.crowded.core.event.infrastructure.persistence.entity.WeatherSnapshotEntity;
import com.crowded.core.event.infrastructure.persistence.mapper.WeatherSnapshotJson;
import java.lang.reflect.Field;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Core reads the JSON Jobs writes in {@code weather_snapshot}. */
class WeatherSnapshotsAdapterTest {

  private static final Instant FETCHED = Instant.parse("2026-11-27T10:05:00Z");
  private static final District PORTO = new District("Porto");

  private final SpringDataWeatherSnapshotRepository rows =
      mock(SpringDataWeatherSnapshotRepository.class);
  private final WeatherSnapshotsAdapter adapter =
      new WeatherSnapshotsAdapter(rows, new WeatherSnapshotJson(JsonMapper.builder().build()));

  @Test
  void readsTheLatestWarningsLikeTheSnapshot() {
    UUID id = UUID.randomUUID();
    when(rows.findLatestLike(id))
        .thenReturn(
            Optional.of(
                row(
                    "WARNINGS",
                    """
                    {"warnings": [{"awarenessType": "Vento", "level": "ORANGE",
                      "start": "2026-11-27T18:00:00Z", "end": "2026-11-28T03:00:00Z",
                      "description": "Rajadas até 90 km/h."},
                     {"awarenessType": "Nevoeiro", "level": "YELLOW",
                      "start": "2026-11-27T18:00:00Z", "end": "2026-11-28T03:00:00Z",
                      "description": null}]}""")));

    assertThat(adapter.findLatestLike(id))
        .contains(
            new WarningsSnapshot(
                PORTO,
                FETCHED,
                List.of(
                    new IssuedWarning(
                        "Vento",
                        WarningLevel.ORANGE,
                        Instant.parse("2026-11-27T18:00:00Z"),
                        Instant.parse("2026-11-28T03:00:00Z"),
                        "Rajadas até 90 km/h."),
                    new IssuedWarning(
                        "Nevoeiro",
                        WarningLevel.YELLOW,
                        Instant.parse("2026-11-27T18:00:00Z"),
                        Instant.parse("2026-11-28T03:00:00Z"),
                        null))));
  }

  @Test
  void readsTheForecastWithIpmasUpdateTime() {
    when(rows.findLatestOfEachKind("Porto"))
        .thenReturn(
            List.of(
                row(
                    "FORECAST",
                    """
                    {"updatedAt": "2026-11-27T09:31:02Z", "days": [{"date": "2026-11-27",
                      "conditions": "Chuvisco", "minTemperature": 9.5, "maxTemperature": 14,
                      "precipitationProbability": 80}]}""")));

    assertThat(adapter.findLatest(PORTO))
        .containsExactly(
            new ForecastSnapshot(
                PORTO,
                List.of(
                    new WeatherForecast(
                        LocalDate.parse("2026-11-27"),
                        "Chuvisco",
                        9.5,
                        14,
                        80,
                        Provenance.ipma(Instant.parse("2026-11-27T09:31:02Z"))))));
  }

  @Test
  void refusesAKindItDoesNotKnow() {
    UUID id = UUID.randomUUID();
    when(rows.findLatestLike(id)).thenReturn(Optional.of(row("TIDES", "{}")));

    assertThatThrownBy(() -> adapter.findLatestLike(id)).isInstanceOf(IllegalStateException.class);
  }

  /** A row as Hibernate reads it: the entity has no setters, being read only. */
  private static WeatherSnapshotEntity row(String kind, String content) {
    WeatherSnapshotEntity entity = new WeatherSnapshotEntity();
    set(entity, "id", UUID.randomUUID());
    set(entity, "district", "Porto");
    set(entity, "kind", kind);
    set(entity, "content", content);
    set(entity, "fetchedAt", FETCHED);
    return entity;
  }

  private static void set(WeatherSnapshotEntity entity, String name, Object value) {
    try {
      Field field = WeatherSnapshotEntity.class.getDeclaredField(name);
      field.setAccessible(true);
      field.set(entity, value);
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException(e);
    }
  }
}
