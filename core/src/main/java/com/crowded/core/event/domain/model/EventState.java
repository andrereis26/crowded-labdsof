package com.crowded.core.event.domain.model;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The part of an event that an official change can alter: its status, schedule and venue. */
public record EventState(
    EventStatus status,
    Instant startDateTime,
    Instant doorsOpenAt,
    Instant endDateTime,
    String venueName,
    String address,
    Double latitude,
    Double longitude) {

  /**
   * Every field, by its API name, with instants and enums as their ISO-8601 or constant text, as a
   * change records them.
   */
  public Map<String, Object> values() {
    Map<String, Object> fields = new LinkedHashMap<>();
    fields.put("status", text(status));
    fields.put("startDateTime", text(startDateTime));
    fields.put("doorsOpenAt", text(doorsOpenAt));
    fields.put("endDateTime", text(endDateTime));
    fields.put("venueName", venueName);
    fields.put("address", address);
    fields.put("latitude", latitude);
    fields.put("longitude", longitude);
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
