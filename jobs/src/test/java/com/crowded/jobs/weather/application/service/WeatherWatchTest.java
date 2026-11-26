package com.crowded.jobs.weather.application.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.jobs.weather.domain.exception.WeatherUnavailableException;
import com.crowded.jobs.weather.domain.model.Forecast;
import com.crowded.jobs.weather.domain.model.ForecastDay;
import com.crowded.jobs.weather.domain.model.SnapshotKind;
import com.crowded.jobs.weather.domain.model.Warning;
import com.crowded.jobs.weather.domain.model.WarningLevel;
import com.crowded.jobs.weather.domain.model.Warnings;
import com.crowded.jobs.weather.domain.model.WeatherSnapshot;
import com.crowded.jobs.weather.domain.model.WeatherUpdated;
import com.crowded.jobs.weather.domain.repository.WeatherSnapshotRepository;
import com.crowded.jobs.weather.domain.repository.WeatherSource;
import com.crowded.jobs.weather.domain.repository.WeatherUpdates;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The weather watch keeps and announces only what changed, and only valid data. */
class WeatherWatchTest {

  private static final Instant NOW = Instant.parse("2026-11-27T10:00:00Z");
  private static final Warning WIND =
      new Warning(
          "Vento",
          WarningLevel.ORANGE,
          Instant.parse("2026-11-27T18:00:00Z"),
          Instant.parse("2026-11-28T03:00:00Z"),
          "Rajadas até 90 km/h.");
  private static final Forecast FORECAST =
      new Forecast(
          NOW.minusSeconds(3600),
          List.of(new ForecastDay(LocalDate.parse("2026-11-27"), "Céu limpo", 8, 16, 0)));

  private final Set<String> districts = new LinkedHashSet<>(Set.of("Porto"));
  private final FakeIpma ipma = new FakeIpma();
  private final InMemorySnapshots snapshots = new InMemorySnapshots();
  private final FakeUpdates updates = new FakeUpdates();
  private Instant now = NOW;
  private Instant watchedUntil;

  private final WeatherWatch watch =
      new WeatherWatch(
          (from, until) -> {
            watchedUntil = until;
            return districts;
          },
          ipma,
          snapshots,
          updates,
          new TickingClock(),
          new SimpleMeterRegistry(),
          Duration.ofHours(1));

  @Test
  void keepsAndAnnouncesTheFirstWarningsAndForecastOfADistrict() {
    ipma.warnings.put("Porto", new Warnings(List.of(WIND)));
    ipma.forecasts.put("Porto", FORECAST);

    watch.check();

    assertThat(watchedUntil).isEqualTo(NOW.plus(Duration.ofDays(5)));
    assertThat(snapshots.stored)
        .extracting(WeatherSnapshot::kind)
        .containsExactly(SnapshotKind.WARNINGS, SnapshotKind.FORECAST);
    assertThat(snapshots.stored).allMatch(snapshot -> snapshot.getPublishedAt() != null);
    assertThat(updates.published)
        .containsExactly(
            new WeatherUpdated("Porto", snapshots.stored.get(0).getId()),
            new WeatherUpdated("Porto", snapshots.stored.get(1).getId()));
  }

  @Test
  void keepsNothingNewWhenIpmaSaysTheSameAgain() {
    ipma.warnings.put("Porto", new Warnings(List.of(WIND)));
    ipma.forecasts.put("Porto", FORECAST);
    watch.check();

    now = NOW.plus(Duration.ofHours(2));
    watch.check();

    assertThat(snapshots.stored).hasSize(2);
    assertThat(updates.published).hasSize(2);
    assertThat(ipma.forecastCalls).isEqualTo(2);
  }

  @Test
  void keepsTheChangedWarningsOfADistrict() {
    ipma.warnings.put("Porto", new Warnings(List.of(WIND)));
    watch.check();

    ipma.warnings.put("Porto", new Warnings(List.of()));
    now = NOW.plusSeconds(300);
    watch.check();

    assertThat(snapshots.stored)
        .filteredOn(snapshot -> snapshot.kind() == SnapshotKind.WARNINGS)
        .extracting(WeatherSnapshot::getContent)
        .containsExactly(new Warnings(List.of(WIND)), new Warnings(List.of()));
  }

  @Test
  void asksForADistrictsForecastOnlyOnceAnHour() {
    ipma.forecasts.put("Porto", FORECAST);
    watch.check();

    now = NOW.plusSeconds(300);
    watch.check();

    assertThat(ipma.forecastCalls).isEqualTo(1);
  }

  @Test
  void asksForAnInvalidForecastAgainOnTheNextRun() {
    watch.check();

    now = NOW.plusSeconds(300);
    ipma.forecasts.put("Porto", FORECAST);
    watch.check();

    assertThat(ipma.forecastCalls).isEqualTo(2);
    assertThat(snapshots.stored)
        .extracting(WeatherSnapshot::kind)
        .containsExactly(SnapshotKind.FORECAST);
  }

  @Test
  void keepsTheLastValidDataWhenIpmaIsUnavailable() {
    ipma.warnings.put("Porto", new Warnings(List.of(WIND)));
    watch.check();

    ipma.unavailable = true;
    now = NOW.plusSeconds(300);
    watch.check();

    assertThat(snapshots.stored).hasSize(1);
    assertThat(ipma.forecastCalls).isEqualTo(1);
  }

  @Test
  void stopsAskingForForecastsInARunOnceIpmaStopsAnswering() {
    districts.add("Lisboa");
    ipma.forecastsUnavailable = true;

    watch.check();

    assertThat(ipma.forecastCalls).isEqualTo(1);
  }

  @Test
  void asksNothingWithoutUpcomingEvents() {
    districts.clear();

    watch.check();

    assertThat(ipma.warningCalls).isZero();
    assertThat(snapshots.stored).isEmpty();
  }

  @Test
  void publishesAgainASnapshotTheBrokerDidNotConfirm() {
    ipma.warnings.put("Porto", new Warnings(List.of(WIND)));
    updates.confirm = false;
    watch.check();
    assertThat(snapshots.stored.getFirst().getPublishedAt()).isNull();

    updates.confirm = true;
    now = NOW.plusSeconds(300);
    watch.check();

    assertThat(snapshots.stored).hasSize(1);
    assertThat(snapshots.stored.getFirst().getPublishedAt()).isNotNull();
    assertThat(updates.published)
        .containsExactly(new WeatherUpdated("Porto", snapshots.stored.getFirst().getId()));
  }

  /** A clock that reads the test's {@code now}. */
  private final class TickingClock extends Clock {

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return now;
    }
  }

  /** IPMA as the test sets it: a district without data in the maps has invalid data. */
  private static final class FakeIpma implements WeatherSource {

    final Map<String, Warnings> warnings = new HashMap<>();
    final Map<String, Forecast> forecasts = new HashMap<>();
    boolean unavailable;
    boolean forecastsUnavailable;
    int warningCalls;
    int forecastCalls;

    @Override
    public Map<String, Warnings> warnings(Set<String> districts) {
      warningCalls++;
      if (unavailable) {
        throw new WeatherUnavailableException("down", null);
      }
      Map<String, Warnings> found = new HashMap<>(warnings);
      found.keySet().retainAll(districts);
      return found;
    }

    @Override
    public Optional<Forecast> forecast(String district) {
      forecastCalls++;
      if (unavailable || forecastsUnavailable) {
        throw new WeatherUnavailableException("down", null);
      }
      return Optional.ofNullable(forecasts.get(district));
    }
  }

  private static final class InMemorySnapshots implements WeatherSnapshotRepository {

    final List<WeatherSnapshot> stored = new ArrayList<>();

    @Override
    public Optional<WeatherSnapshot> findLatest(String district, SnapshotKind kind) {
      return stored.stream()
          .filter(s -> s.getDistrict().equals(district) && s.kind() == kind)
          .max(Comparator.comparing(WeatherSnapshot::getFetchedAt));
    }

    @Override
    public void add(WeatherSnapshot snapshot) {
      stored.add(snapshot);
    }

    @Override
    public List<WeatherSnapshot> findUnpublished(int limit) {
      return stored.stream().filter(s -> s.getPublishedAt() == null).limit(limit).toList();
    }

    @Override
    public void save(WeatherSnapshot snapshot) {
      // The stored object is the same one, already changed.
    }
  }

  private static final class FakeUpdates implements WeatherUpdates {

    final List<WeatherUpdated> published = new ArrayList<>();
    boolean confirm = true;

    @Override
    public boolean publish(WeatherUpdated message) {
      if (confirm) {
        published.add(message);
      }
      return confirm;
    }
  }
}
