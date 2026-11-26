package com.crowded.core.event.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * An IPMA warning for an event's district during the event. It remembers the highest level its
 * participants were notified of, so each level is notified once however often IPMA repeats it.
 */
public final class WeatherWarning {

  private final UUID id;
  private final UUID eventId;
  private final String awarenessType;
  private final Instant start;
  private Instant end;
  private WarningLevel level;
  private WarningLevel notifiedLevel;
  private String description;
  private Provenance provenance;

  private WeatherWarning(
      UUID id,
      UUID eventId,
      String awarenessType,
      Instant start,
      Instant end,
      WarningLevel level,
      WarningLevel notifiedLevel,
      String description,
      Provenance provenance) {
    this.id = Objects.requireNonNull(id, "id");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.awarenessType = Objects.requireNonNull(awarenessType, "awarenessType");
    this.start = Objects.requireNonNull(start, "start");
    this.end = Objects.requireNonNull(end, "end");
    this.level = Objects.requireNonNull(level, "level");
    this.notifiedLevel = notifiedLevel;
    this.description = description;
    this.provenance = Objects.requireNonNull(provenance, "provenance");
  }

  /** The warning IPMA issued, seen for the event at {@code now}; nobody is notified of it yet. */
  static WeatherWarning issue(UUID eventId, IssuedWarning issued, Instant now) {
    return new WeatherWarning(
        UUID.randomUUID(),
        eventId,
        issued.awarenessType(),
        issued.start(),
        issued.end(),
        issued.level(),
        null,
        issued.description(),
        Provenance.ipma(now));
  }

  /** A stored warning, as it was saved. */
  public static WeatherWarning restore(
      UUID id,
      UUID eventId,
      String awarenessType,
      Instant start,
      Instant end,
      WarningLevel level,
      WarningLevel notifiedLevel,
      String description,
      Provenance provenance) {
    return new WeatherWarning(
        id, eventId, awarenessType, start, end, level, notifiedLevel, description, provenance);
  }

  /** Takes IPMA's new level, end and text; its time changes only when one of them did. */
  void revise(IssuedWarning issued, Instant now) {
    if (level == issued.level()
        && end.equals(issued.end())
        && Objects.equals(description, issued.description())) {
      return;
    }
    level = issued.level();
    end = issued.end();
    description = issued.description();
    provenance = Provenance.ipma(now);
  }

  /** Whether its level is notified and higher than any its participants were notified of. */
  boolean needsNotification() {
    return level.isNotified() && (notifiedLevel == null || level.compareTo(notifiedLevel) > 0);
  }

  /** Its participants are being notified of its current level. */
  void markNotified() {
    notifiedLevel = level;
  }

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public String getAwarenessType() {
    return awarenessType;
  }

  public Instant getStart() {
    return start;
  }

  public Instant getEnd() {
    return end;
  }

  public WarningLevel getLevel() {
    return level;
  }

  /** The highest level its participants were notified of; null before any. */
  public WarningLevel getNotifiedLevel() {
    return notifiedLevel;
  }

  /** IPMA's text; null when it gave none. */
  public String getDescription() {
    return description;
  }

  public Provenance getProvenance() {
    return provenance;
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
    return "WeatherWarning[" + id + ", " + awarenessType + ", " + level + "]";
  }
}
