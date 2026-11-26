package com.crowded.notifications.notification.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * An IPMA warning for an event's district that Core asked to notify. Every participant reads the
 * same text: its level and what it warns of, IPMA's description and when it is in force, in the
 * district's time. A red warning says it is critical. Notifications only reads it: Core owns it.
 */
public final class WeatherWarning {

  private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
  private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd/MM");

  private final UUID id;
  private final UUID eventId;
  private final String awarenessType;
  private final WarningLevel level;
  private final Instant start;
  private final Instant end;
  private final String description;
  private final ZoneId zone;

  private WeatherWarning(
      UUID id,
      UUID eventId,
      String awarenessType,
      WarningLevel level,
      Instant start,
      Instant end,
      String description,
      ZoneId zone) {
    this.id = Objects.requireNonNull(id, "id");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.awarenessType = Objects.requireNonNull(awarenessType, "awarenessType");
    this.level = Objects.requireNonNull(level, "level");
    this.start = Objects.requireNonNull(start, "start");
    this.end = Objects.requireNonNull(end, "end");
    this.description = description;
    this.zone = Objects.requireNonNull(zone, "zone");
  }

  /**
   * The warning as Core stored it.
   *
   * @param zone the legal time of the event's district
   */
  public static WeatherWarning restore(
      UUID id,
      UUID eventId,
      String awarenessType,
      WarningLevel level,
      Instant start,
      Instant end,
      String description,
      ZoneId zone) {
    return new WeatherWarning(id, eventId, awarenessType, level, start, end, description, zone);
  }

  /** Whether this warning is about the event {@code event}. */
  public boolean isOf(UUID event) {
    return eventId.equals(event);
  }

  /**
   * "Aviso laranja de vento", with "Crítico" before a red one, and "Rajadas até 90 km/h. Em vigor
   * das 18:00 de 27/11 às 03:00 de 28/11."
   */
  public NotificationText describe() {
    String title =
        "Aviso " + level.label() + " de " + awarenessType.strip().toLowerCase(Locale.ROOT);
    String text = description == null || description.isBlank() ? "" : description.strip() + " ";
    return new NotificationText(
        level == WarningLevel.RED ? "Crítico · " + title : title,
        text + "Em vigor " + period() + ".");
  }

  /** "das 18:00 às 23:59 de 27/11", or with both days when it ends on another one. */
  private String period() {
    ZonedDateTime from = start.atZone(zone);
    ZonedDateTime to = end.atZone(zone);
    LocalDate firstDay = from.toLocalDate();
    if (firstDay.equals(to.toLocalDate())) {
      return "das " + TIME.format(from) + " às " + TIME.format(to) + " de " + DAY.format(from);
    }
    return "das "
        + TIME.format(from)
        + " de "
        + DAY.format(from)
        + " às "
        + TIME.format(to)
        + " de "
        + DAY.format(to);
  }

  public UUID getId() {
    return id;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof WeatherWarning warning && id.equals(warning.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "WeatherWarning[" + id + ", " + level + "]";
  }
}
