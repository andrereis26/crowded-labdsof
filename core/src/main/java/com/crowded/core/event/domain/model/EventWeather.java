package com.crowded.core.event.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * What participants read about an event's weather: IPMA's forecast for its days and the warnings
 * for its district while it takes place. It changes only by applying what Jobs fetched, which also
 * says which warnings to notify.
 */
public final class EventWeather {

  private final UUID eventId;
  private List<WeatherForecast> forecast;
  private final List<WeatherWarning> warnings;

  private EventWeather(
      UUID eventId, List<WeatherForecast> forecast, List<WeatherWarning> warnings) {
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.forecast = List.copyOf(forecast);
    this.warnings = new ArrayList<>(warnings);
  }

  /** The weather of an event IPMA has said nothing about yet. */
  public static EventWeather none(UUID eventId) {
    return new EventWeather(eventId, List.of(), List.of());
  }

  /** The stored weather of an event. */
  public static EventWeather restore(
      UUID eventId, List<WeatherForecast> forecast, List<WeatherWarning> warnings) {
    return new EventWeather(eventId, forecast, warnings);
  }

  /**
   * Applies what IPMA says about {@code event}'s district: the forecast of its days, or the
   * warnings that overlap it, replacing what IPMA no longer says. Each warning that is new or
   * raised to orange or red is marked notified at its level.
   *
   * @return the warnings to notify its participants of; none for a forecast
   */
  public List<WeatherWarning> apply(WeatherSnapshot snapshot, Event event, Instant now) {
    Schedule schedule = event.getState().schedule();
    return switch (snapshot) {
      case ForecastSnapshot forecastSnapshot -> {
        Set<LocalDate> days = schedule.days(event.getDistrict().zone());
        forecast =
            forecastSnapshot.days().stream().filter(day -> days.contains(day.date())).toList();
        yield List.of();
      }
      case WarningsSnapshot warningsSnapshot ->
          warn(
              warningsSnapshot.warnings().stream()
                  .filter(issued -> schedule.overlaps(issued.start(), issued.end()))
                  .toList(),
              now);
    };
  }

  private List<WeatherWarning> warn(List<IssuedWarning> issued, Instant now) {
    warnings.removeIf(warning -> issued.stream().noneMatch(found -> found.isSameAs(warning)));
    for (IssuedWarning found : issued) {
      warnings.stream()
          .filter(found::isSameAs)
          .findFirst()
          .ifPresentOrElse(
              warning -> warning.revise(found, now),
              () -> warnings.add(WeatherWarning.issue(eventId, found, now)));
    }
    List<WeatherWarning> toNotify =
        warnings.stream().filter(WeatherWarning::needsNotification).toList();
    toNotify.forEach(WeatherWarning::markNotified);
    return toNotify;
  }

  public UUID getEventId() {
    return eventId;
  }

  /** The forecast of the event's days that IPMA has, the earliest first. */
  public List<WeatherForecast> getForecast() {
    return forecast.stream().sorted(Comparator.comparing(WeatherForecast::date)).toList();
  }

  /** The warnings during the event, the most severe first, then the earliest. */
  public List<WeatherWarning> getWarnings() {
    return warnings.stream()
        .sorted(
            Comparator.comparing(WeatherWarning::getLevel)
                .reversed()
                .thenComparing(WeatherWarning::getStart))
        .toList();
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof EventWeather weather && eventId.equals(weather.eventId);
  }

  @Override
  public int hashCode() {
    return eventId.hashCode();
  }

  @Override
  public String toString() {
    return "EventWeather[" + eventId + ", " + warnings.size() + " warning(s)]";
  }
}
