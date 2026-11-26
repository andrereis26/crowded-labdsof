package com.crowded.core.event.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventState;
import com.crowded.core.event.domain.model.EventStatus;
import com.crowded.core.event.domain.model.EventWeather;
import com.crowded.core.event.domain.model.ForecastSnapshot;
import com.crowded.core.event.domain.model.IssuedWarning;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.Schedule;
import com.crowded.core.event.domain.model.StoredEvents;
import com.crowded.core.event.domain.model.Venue;
import com.crowded.core.event.domain.model.WarningLevel;
import com.crowded.core.event.domain.model.WarningsSnapshot;
import com.crowded.core.event.domain.model.WeatherForecast;
import com.crowded.core.event.domain.model.WeatherSnapshot;
import com.crowded.core.event.domain.model.WeatherWarning;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.EventUpdates;
import com.crowded.core.event.domain.repository.EventWeatherRepository;
import com.crowded.core.event.domain.repository.WeatherSnapshots;
import com.crowded.core.outbox.application.service.NotificationRequester;
import com.crowded.core.shared.domain.exception.NotFoundException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Jobs' weather reaches the events of the district, their pages and their participants. */
class WeatherServiceTest {

  private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
  private static final Instant START = Instant.parse("2026-11-27T21:00:00Z");
  private static final District PORTO = new District("Porto");
  private static final UUID SNAPSHOT = UUID.randomUUID();

  private final WeatherSnapshots snapshots = mock(WeatherSnapshots.class);
  private final EventRepository events = mock(EventRepository.class);
  private final InMemoryWeathers weathers = new InMemoryWeathers();
  private final NotificationRequester notifications = mock(NotificationRequester.class);
  private final EventUpdates updates = mock(EventUpdates.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private final WeatherService service =
      new WeatherService(
          snapshots,
          events,
          weathers,
          notifications,
          updates,
          Clock.fixed(NOW, ZoneOffset.UTC),
          meters);

  private final Event event =
      StoredEvents.restore(
          UUID.randomUUID(),
          UUID.randomUUID(),
          new EventState(
              EventStatus.SCHEDULED,
              new Schedule(START, null, START.plusSeconds(3 * 3600)),
              new Venue("Coliseu", "Porto", null)));

  @BeforeEach
  void givenAnUpcomingEventInPorto() {
    when(events.findUpcomingInDistrict(PORTO, NOW)).thenReturn(List.of(event));
    when(events.findByIdForUpdate(event.getId())).thenReturn(Optional.of(event));
  }

  @Test
  void storesTheWarningsNotifiesTheOrangeOneAndRefreshesThePage() {
    givenSnapshot(
        new WarningsSnapshot(
            PORTO,
            NOW,
            List.of(
                warning("Vento", WarningLevel.ORANGE), warning("Nevoeiro", WarningLevel.YELLOW))));

    service.apply(SNAPSHOT);

    List<WeatherWarning> stored = weathers.findByEvent(event.getId()).getWarnings();
    assertThat(stored).hasSize(2);
    WeatherWarning orange = stored.getFirst();
    assertThat(orange.getLevel()).isEqualTo(WarningLevel.ORANGE);
    verify(notifications).request("WEATHER_WARNING", event.getId(), orange.getId());
    verify(notifications, times(1)).request(anyString(), any(), any());
    verify(updates).announce(event.getId());
    assertThat(
            meters
                .get("crowded.weather.warnings.notified")
                .tag("level", "ORANGE")
                .counter()
                .count())
        .isOne();
  }

  @Test
  void notifiesNoOneWhenTheSameSnapshotArrivesAgain() {
    givenSnapshot(new WarningsSnapshot(PORTO, NOW, List.of(warning("Vento", WarningLevel.RED))));

    service.apply(SNAPSHOT);
    service.apply(SNAPSHOT);

    verify(notifications)
        .request(
            "WEATHER_WARNING",
            event.getId(),
            weathers.findByEvent(event.getId()).getWarnings().getFirst().getId());
  }

  @Test
  void storesTheForecastOfTheEventsDay() {
    WeatherForecast day =
        new WeatherForecast(
            LocalDate.parse("2026-11-27"), "Chuvisco", 9, 14, 80, Provenance.ipma(NOW));
    givenSnapshot(new ForecastSnapshot(PORTO, List.of(day)));

    service.apply(SNAPSHOT);

    assertThat(weathers.findByEvent(event.getId()).getForecast()).containsExactly(day);
    verify(updates).announce(event.getId());
  }

  @Test
  void refusesASnapshotJobsDoesNotHave() {
    when(snapshots.findLatestLike(SNAPSHOT)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.apply(SNAPSHOT)).isInstanceOf(NotFoundException.class);
  }

  @Test
  void refreshesAnEventWithItsDistrictsLatestWeather() {
    when(snapshots.findLatest(PORTO))
        .thenReturn(
            List.of(new WarningsSnapshot(PORTO, NOW, List.of(warning("Vento", WarningLevel.RED)))));

    service.refresh(event);

    assertThat(weathers.findByEvent(event.getId()).getWarnings()).hasSize(1);
    verify(updates).announce(event.getId());
  }

  private void givenSnapshot(WeatherSnapshot snapshot) {
    when(snapshots.findLatestLike(SNAPSHOT)).thenReturn(Optional.of(snapshot));
  }

  private static IssuedWarning warning(String type, WarningLevel level) {
    return new IssuedWarning(type, level, START, START.plusSeconds(3600), null);
  }

  /** Keeps each event's weather object, as the database keeps its rows. */
  private static final class InMemoryWeathers implements EventWeatherRepository {

    private final Map<UUID, EventWeather> stored = new HashMap<>();

    @Override
    public EventWeather findByEvent(UUID eventId) {
      return stored.getOrDefault(eventId, EventWeather.none(eventId));
    }

    @Override
    public void save(EventWeather weather) {
      stored.put(weather.getEventId(), weather);
    }
  }
}
