package com.crowded.jobs.weather.application.service;

import com.crowded.jobs.weather.domain.exception.WeatherUnavailableException;
import com.crowded.jobs.weather.domain.model.WeatherContent;
import com.crowded.jobs.weather.domain.model.WeatherSnapshot;
import com.crowded.jobs.weather.domain.repository.EventDistricts;
import com.crowded.jobs.weather.domain.repository.WeatherSnapshotRepository;
import com.crowded.jobs.weather.domain.repository.WeatherSource;
import com.crowded.jobs.weather.domain.repository.WeatherUpdates;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Watches IPMA for the districts of the events of the next days (Asynchronous Flows, weather
 * warning flow): their warnings on every run and each district's forecast once per {@code
 * forecastInterval}. What differs from the district's latest snapshot is stored and announced to
 * Core; what IPMA did not send, or sent invalid, leaves the last valid snapshot in place.
 */
@Service
public class WeatherWatch {

  private static final Logger log = LoggerFactory.getLogger(WeatherWatch.class);

  /** IPMA forecasts five days, and warns at most three days ahead. */
  static final Duration HORIZON = Duration.ofDays(5);

  /** Messages per run; the rest wait for the next one. */
  static final int BATCH_SIZE = 100;

  private final EventDistricts districts;
  private final WeatherSource ipma;
  private final WeatherSnapshotRepository snapshots;
  private final WeatherUpdates updates;
  private final Clock clock;
  private final MeterRegistry meters;
  private final Duration forecastInterval;

  /** When each district's forecast was last fetched; empty after a restart, so all are fetched. */
  private final Map<String, Instant> forecastFetchedAt = new ConcurrentHashMap<>();

  WeatherWatch(
      EventDistricts districts,
      WeatherSource ipma,
      WeatherSnapshotRepository snapshots,
      WeatherUpdates updates,
      Clock clock,
      MeterRegistry meters,
      @Value("${crowded.weather.forecast-interval:1h}") Duration forecastInterval) {
    this.districts = districts;
    this.ipma = ipma;
    this.snapshots = snapshots;
    this.updates = updates;
    this.clock = clock;
    this.meters = meters;
    this.forecastInterval = forecastInterval;
  }

  /** One run: fetches what is due, then publishes every snapshot not confirmed yet. */
  public void check() {
    Instant now = clock.instant();
    Set<String> watched = districts.withEventsBetween(now, now.plus(HORIZON));
    if (!watched.isEmpty() && checkWarnings(watched, now)) {
      checkForecasts(watched, now);
    }
    publishWaiting();
  }

  /** Whether IPMA answered; when it did not, its forecasts are not asked for in this run either. */
  private boolean checkWarnings(Set<String> watched, Instant now) {
    try {
      ipma.warnings(watched).forEach((district, warnings) -> keepIfNew(district, warnings, now));
      return true;
    } catch (WeatherUnavailableException e) {
      unavailable("warnings", e);
      return false;
    }
  }

  private void checkForecasts(Set<String> watched, Instant now) {
    for (String district : watched) {
      Instant last = forecastFetchedAt.get(district);
      if (last != null && last.plus(forecastInterval).isAfter(now)) {
        continue;
      }
      try {
        // An invalid forecast is asked for again on the next run.
        ipma.forecast(district)
            .ifPresent(
                forecast -> {
                  keepIfNew(district, forecast, now);
                  forecastFetchedAt.put(district, now);
                });
      } catch (WeatherUnavailableException e) {
        unavailable("forecast", e);
        return;
      }
    }
  }

  private void keepIfNew(String district, WeatherContent content, Instant now) {
    WeatherSnapshot snapshot = WeatherSnapshot.take(district, content, now);
    if (!snapshot.isNewComparedTo(snapshots.findLatest(district, snapshot.kind()))) {
      return;
    }
    snapshots.add(snapshot);
    Counter.builder("crowded.weather.snapshots")
        .description("Weather snapshots that changed what IPMA says about a district")
        .tag("kind", snapshot.kind().name())
        .register(meters)
        .increment();
    log.info("New {} snapshot {} for {}", snapshot.kind(), snapshot.getId(), district);
  }

  /** Publishes the waiting snapshots, oldest first, and stops at the first one not confirmed. */
  private void publishWaiting() {
    for (WeatherSnapshot snapshot : snapshots.findUnpublished(BATCH_SIZE)) {
      if (!updates.publish(snapshot.announcement())) {
        return;
      }
      snapshot.markPublished(clock.instant());
      snapshots.save(snapshot);
    }
  }

  private void unavailable(String what, WeatherUnavailableException e) {
    Counter.builder("crowded.weather.unavailable")
        .description("Runs in which IPMA did not answer")
        .tag("data", what)
        .register(meters)
        .increment();
    log.warn("IPMA {} unavailable, keeping the last valid data", what, e);
  }
}
