package com.crowded.core.event.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.event.domain.model.EventWeather;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.WarningLevel;
import com.crowded.core.event.domain.model.WeatherForecast;
import com.crowded.core.event.domain.model.WeatherWarning;
import com.crowded.core.event.infrastructure.persistence.entity.WeatherForecastEntity;
import com.crowded.core.event.infrastructure.persistence.entity.WeatherWarningEntity;
import com.crowded.core.event.infrastructure.persistence.mapper.WeatherPersistenceMapperImpl;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/** An event's weather goes to its two tables and comes back equal. */
class EventWeatherRepositoryAdapterTest {

  private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
  private static final UUID EVENT = UUID.randomUUID();
  private static final WeatherForecast DAY =
      new WeatherForecast(
          LocalDate.parse("2026-11-27"), "Chuvisco", 9, 14, 80, Provenance.ipma(NOW));
  private static final WeatherWarning WARNING =
      WeatherWarning.restore(
          UUID.randomUUID(),
          EVENT,
          "Vento",
          NOW,
          NOW.plusSeconds(3600),
          WarningLevel.RED,
          WarningLevel.ORANGE,
          "Rajadas.",
          Provenance.ipma(NOW));

  private final SpringDataWeatherForecastRepository forecasts =
      mock(SpringDataWeatherForecastRepository.class);
  private final SpringDataWeatherWarningRepository warnings =
      mock(SpringDataWeatherWarningRepository.class);
  private final EventWeatherRepositoryAdapter adapter =
      new EventWeatherRepositoryAdapter(forecasts, warnings, new WeatherPersistenceMapperImpl());

  @Test
  void replacesTheForecastAndKeepsOnlyTheCurrentWarnings() {
    adapter.save(EventWeather.restore(EVENT, List.of(DAY), List.of(WARNING)));

    verify(forecasts).deleteByEvent(EVENT);
    ArgumentCaptor<WeatherForecastEntity> day =
        ArgumentCaptor.forClass(WeatherForecastEntity.class);
    verify(forecasts).save(day.capture());
    assertThat(day.getValue().getEventId()).isEqualTo(EVENT);
    assertThat(day.getValue().getProvenanceSourceName()).isEqualTo("IPMA");
    verify(warnings).deleteByEventExcept(EVENT, List.of(WARNING.getId()));
    ArgumentCaptor<WeatherWarningEntity> warning =
        ArgumentCaptor.forClass(WeatherWarningEntity.class);
    verify(warnings).save(warning.capture());
    assertThat(warning.getValue().getStartTime()).isEqualTo(NOW);
    assertThat(warning.getValue().getNotifiedLevel()).isEqualTo(WarningLevel.ORANGE);

    when(forecasts.findByEventId(EVENT)).thenReturn(List.of(day.getValue()));
    when(warnings.findByEventId(EVENT)).thenReturn(List.of(warning.getValue()));
    EventWeather read = adapter.findByEvent(EVENT);

    assertThat(read.getForecast()).containsExactly(DAY);
    assertThat(read.getWarnings()).containsExactly(WARNING);
    WeatherWarning restored = read.getWarnings().getFirst();
    assertThat(restored.getNotifiedLevel()).isEqualTo(WarningLevel.ORANGE);
    assertThat(restored.getDescription()).isEqualTo("Rajadas.");
    assertThat(restored.getProvenance()).isEqualTo(Provenance.ipma(NOW));
  }

  @Test
  void deletesEveryWarningWhenIpmaHasNone() {
    adapter.save(EventWeather.none(EVENT));

    verify(warnings).deleteByEvent(EVENT);
    verify(warnings, never()).deleteByEventExcept(any(), any());
  }
}
