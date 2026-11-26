package com.crowded.core.event.application.service;

import com.crowded.core.event.domain.model.Event;
import com.crowded.core.event.domain.model.EventWeather;
import com.crowded.core.event.domain.model.WeatherSnapshot;
import com.crowded.core.event.domain.model.WeatherWarning;
import com.crowded.core.event.domain.repository.EventRepository;
import com.crowded.core.event.domain.repository.EventUpdates;
import com.crowded.core.event.domain.repository.EventWeatherRepository;
import com.crowded.core.event.domain.repository.WeatherSnapshots;
import com.crowded.core.outbox.application.service.NotificationRequester;
import com.crowded.core.shared.domain.exception.NotFoundException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Applies what Jobs fetched from IPMA to the events of a district (Asynchronous Flows, weather
 * warning flow): stores their forecast and warnings, asks Notifications to notify each warning that
 * is new or raised to orange or red, and sends the new page to their participants, live.
 */
@Service
public class WeatherService {

  private static final Logger log = LoggerFactory.getLogger(WeatherService.class);

  /** Notification type of an IPMA warning (API Outline, Messages). */
  static final String WEATHER_WARNING = "WEATHER_WARNING";

  private final WeatherSnapshots snapshots;
  private final EventRepository events;
  private final EventWeatherRepository weathers;
  private final NotificationRequester notifications;
  private final EventUpdates updates;
  private final Clock clock;
  private final MeterRegistry meters;

  WeatherService(
      WeatherSnapshots snapshots,
      EventRepository events,
      EventWeatherRepository weathers,
      NotificationRequester notifications,
      EventUpdates updates,
      Clock clock,
      MeterRegistry meters) {
    this.snapshots = snapshots;
    this.events = events;
    this.weathers = weathers;
    this.notifications = notifications;
    this.updates = updates;
    this.clock = clock;
    this.meters = meters;
  }

  /**
   * Applies the latest snapshot of the same district and kind as {@code snapshotId} to every event
   * there that has not ended. Applying it again changes nothing and notifies no one.
   *
   * @throws NotFoundException when Jobs has no such snapshot
   */
  @Transactional
  public void apply(UUID snapshotId) {
    WeatherSnapshot snapshot =
        snapshots
            .findLatestLike(snapshotId)
            .orElseThrow(
                () ->
                    new NotFoundException(
                        "WEATHER_SNAPSHOT_NOT_FOUND", "There is no such weather snapshot."));
    Instant now = clock.instant();
    List<Event> upcoming = events.findUpcomingInDistrict(snapshot.district(), now);
    for (Event found : upcoming) {
      // Locked, so a change to the event or the other kind of snapshot waits for this one.
      events.findByIdForUpdate(found.getId()).ifPresent(event -> applyTo(event, snapshot, now));
    }
    log.info(
        "Applied weather snapshot {} of {} to {} event(s)",
        snapshotId,
        snapshot.district().name(),
        upcoming.size());
  }

  /**
   * Applies the latest weather of {@code event}'s district to it, after it was registered, moved to
   * another district or rescheduled. The caller holds the event's lock.
   */
  @Transactional(propagation = Propagation.MANDATORY)
  public void refresh(Event event) {
    Instant now = clock.instant();
    snapshots.findLatest(event.getDistrict()).forEach(snapshot -> applyTo(event, snapshot, now));
  }

  private void applyTo(Event event, WeatherSnapshot snapshot, Instant now) {
    EventWeather weather = weathers.findByEvent(event.getId());
    List<WeatherWarning> toNotify = weather.apply(snapshot, event, now);
    weathers.save(weather);
    for (WeatherWarning warning : toNotify) {
      notifications.request(WEATHER_WARNING, event.getId(), warning.getId());
      Counter.builder("crowded.weather.warnings.notified")
          .description("IPMA warnings notified to an event's participants")
          .tag("level", warning.getLevel().name())
          .register(meters)
          .increment();
      log.info(
          "Notifying {} warning {} ({}) to the participants of event {}",
          warning.getLevel(),
          warning.getId(),
          warning.getAwarenessType(),
          event.getId());
    }
    updates.announce(event.getId());
  }
}
