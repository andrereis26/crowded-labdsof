package com.crowded.core.event.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The part of an event that an official change can alter: its status, schedule and venue. */
public record EventState(EventStatus status, Schedule schedule, Venue venue) {

  public EventState {
    Objects.requireNonNull(status, "status");
    Objects.requireNonNull(schedule, "schedule");
    Objects.requireNonNull(venue, "venue");
  }

  /** The same event with another status. */
  public EventState inStatus(EventStatus newStatus) {
    return new EventState(newStatus, schedule, venue);
  }

  /**
   * Every field, by its API name, with instants and enums as their ISO-8601 or constant text, as a
   * change records them.
   */
  public Map<String, Object> values() {
    Coordinates coordinates = venue.coordinates();
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("status", status.name());
    fields.put("startDateTime", text(schedule.start()));
    fields.put("doorsOpenAt", text(schedule.doorsOpenAt()));
    fields.put("endDateTime", text(schedule.end()));
    fields.put("venueName", venue.name());
    fields.put("address", venue.address());
    fields.put("latitude", coordinates == null ? null : coordinates.latitude());
    fields.put("longitude", coordinates == null ? null : coordinates.longitude());
    return fields;
  }

  /** The fields whose value differs in {@code other}, with this state's values. */
  public Map<String, Object> differencesFrom(EventState other) {
    Map<String, Object> mine = values();
    Map<String, Object> theirs = other.values();
    Map<String, Object> differences = new LinkedHashMap<>();
    mine.forEach(
        (name, value) -> {
          if (!Objects.equals(value, theirs.get(name))) {
            differences.put(name, value);
          }
        });
    return differences;
  }

  private static String text(Object value) {
    return value == null ? null : value.toString();
  }
}
