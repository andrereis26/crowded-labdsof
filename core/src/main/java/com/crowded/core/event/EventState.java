package com.crowded.core.event;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** The part of an event that an official change can alter: its status, schedule and venue. */
record EventState(
    EventStatus status,
    Instant startDateTime,
    Instant doorsOpenAt,
    Instant endDateTime,
    String venueName,
    String address,
    Double latitude,
    Double longitude) {

  /** The fields whose value differs in {@code other}, with this state's values. */
  Map<String, Object> differencesFrom(EventState other) {
    Map<String, Object> fields = new LinkedHashMap<>();
    put(fields, "status", status, other.status);
    put(fields, "startDateTime", startDateTime, other.startDateTime);
    put(fields, "doorsOpenAt", doorsOpenAt, other.doorsOpenAt);
    put(fields, "endDateTime", endDateTime, other.endDateTime);
    put(fields, "venueName", venueName, other.venueName);
    put(fields, "address", address, other.address);
    put(fields, "latitude", latitude, other.latitude);
    put(fields, "longitude", longitude, other.longitude);
    return fields;
  }

  private static void put(Map<String, Object> fields, String name, Object value, Object other) {
    if (!Objects.equals(value, other)) {
      // Instants and enums as their ISO-8601 or constant text, as the API sends them.
      fields.put(
          name, value instanceof Instant || value instanceof Enum ? value.toString() : value);
    }
  }
}
