package com.crowded.jobs.weather.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.jobs.weather.domain.model.Forecast;
import com.crowded.jobs.weather.domain.model.ForecastDay;
import com.crowded.jobs.weather.domain.model.SnapshotKind;
import com.crowded.jobs.weather.domain.model.Warning;
import com.crowded.jobs.weather.domain.model.WarningLevel;
import com.crowded.jobs.weather.domain.model.Warnings;
import com.crowded.jobs.weather.domain.model.WeatherSnapshot;
import com.crowded.jobs.weather.infrastructure.persistence.entity.WeatherSnapshotEntity;
import com.crowded.jobs.weather.infrastructure.persistence.mapper.WeatherContentJson;
import com.crowded.jobs.weather.infrastructure.persistence.mapper.WeatherSnapshotPersistenceMapperImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.Limit;
import tools.jackson.databind.json.JsonMapper;

/** Snapshots go to {@code weather_snapshot} as JSON and come back equal. */
class WeatherSnapshotRepositoryAdapterTest {

  private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");

  private final SpringDataWeatherSnapshotRepository rows =
      mock(SpringDataWeatherSnapshotRepository.class);
  private final WeatherSnapshotRepositoryAdapter adapter =
      new WeatherSnapshotRepositoryAdapter(
          rows,
          new WeatherSnapshotPersistenceMapperImpl(),
          new WeatherContentJson(JsonMapper.builder().build()));

  @Test
  void storesWarningsAsJsonAndReadsThemBack() {
    WeatherSnapshot snapshot =
        WeatherSnapshot.take(
            "Porto",
            new Warnings(
                List.of(
                    new Warning(
                        "Vento",
                        WarningLevel.RED,
                        Instant.parse("2026-11-27T18:00:00Z"),
                        Instant.parse("2026-11-28T03:00:00Z"),
                        "Rajadas até 130 km/h."))),
            NOW);

    WeatherSnapshotEntity row = stored(snapshot);

    assertThat(row.getKind()).isEqualTo(SnapshotKind.WARNINGS);
    assertThat(row.getContent())
        .contains("\"awarenessType\":\"Vento\"", "\"start\":\"2026-11-27T18:00:00Z\"");
    assertRoundTrip(snapshot, row);
  }

  @Test
  void storesAForecastAsJsonAndReadsItBack() {
    WeatherSnapshot snapshot =
        WeatherSnapshot.take(
            "Lisboa",
            new Forecast(
                NOW,
                List.of(new ForecastDay(LocalDate.parse("2026-11-27"), "Chuvisco", 9, 14, 80))),
            NOW);
    snapshot.markPublished(NOW.plusSeconds(1));

    WeatherSnapshotEntity row = stored(snapshot);

    assertThat(row.getKind()).isEqualTo(SnapshotKind.FORECAST);
    assertThat(row.getContent()).contains("\"date\":\"2026-11-27\"", "\"updatedAt\"");
    assertThat(row.getPublishedAt()).isEqualTo(NOW.plusSeconds(1));
    assertRoundTrip(snapshot, row);
  }

  @Test
  void readsTheUnpublishedOldestFirst() {
    WeatherSnapshot snapshot = WeatherSnapshot.take("Porto", new Warnings(List.of()), NOW);
    WeatherSnapshotEntity row = stored(snapshot);
    when(rows.findByPublishedAtIsNullOrderByFetchedAt(Limit.of(10))).thenReturn(List.of(row));

    assertThat(adapter.findUnpublished(10)).containsExactly(snapshot);
  }

  @Test
  void savesAPublishedSnapshot() {
    WeatherSnapshot snapshot = WeatherSnapshot.take("Porto", new Warnings(List.of()), NOW);
    snapshot.markPublished(NOW);

    adapter.save(snapshot);

    ArgumentCaptor<WeatherSnapshotEntity> row =
        ArgumentCaptor.forClass(WeatherSnapshotEntity.class);
    verify(rows).save(row.capture());
    assertThat(row.getValue().getPublishedAt()).isEqualTo(NOW);
  }

  private WeatherSnapshotEntity stored(WeatherSnapshot snapshot) {
    ArgumentCaptor<WeatherSnapshotEntity> row =
        ArgumentCaptor.forClass(WeatherSnapshotEntity.class);
    when(rows.save(any())).thenAnswer(call -> call.getArgument(0));
    adapter.add(snapshot);
    verify(rows).save(row.capture());
    return row.getValue();
  }

  private void assertRoundTrip(WeatherSnapshot snapshot, WeatherSnapshotEntity row) {
    when(rows.findFirstByDistrictAndKindOrderByFetchedAtDesc(
            snapshot.getDistrict(), snapshot.kind()))
        .thenReturn(Optional.of(row));

    WeatherSnapshot read =
        adapter.findLatest(snapshot.getDistrict(), snapshot.kind()).orElseThrow();

    assertThat(read).isEqualTo(snapshot);
    assertThat(read.getContent()).isEqualTo(snapshot.getContent());
    assertThat(read.getFetchedAt()).isEqualTo(snapshot.getFetchedAt());
    assertThat(read.getPublishedAt()).isEqualTo(snapshot.getPublishedAt());
  }
}
