package com.crowded.core.event.domain.model;

import java.time.Instant;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An official change to an event (FR-016), with the fields it changed before and after it.
 *
 * @param previousValue the changed fields' values before the change
 * @param newValue the changed fields' values after it
 */
public record EventChange(
    UUID id,
    UUID eventId,
    ChangeType type,
    Severity severity,
    Map<String, Object> previousValue,
    Map<String, Object> newValue,
    Boolean ticketStillValid,
    String refundInfo,
    String message,
    SourceType provenanceSourceType,
    String provenanceSourceName,
    Instant provenanceLastUpdatedAt,
    boolean provenanceConfirmed) {

  public EventChange {
    // Copies that keep the order and allow null values (a cleared coordinate).
    previousValue = Collections.unmodifiableMap(new LinkedHashMap<>(previousValue));
    newValue = Collections.unmodifiableMap(new LinkedHashMap<>(newValue));
  }

  /** The change {@code plan} published by the organization {@code organizationName}. */
  public static EventChange publish(
      UUID eventId, ChangePlan plan, String organizationName, Instant publishedAt) {
    return new EventChange(
        UUID.randomUUID(),
        eventId,
        plan.type(),
        plan.severity(),
        plan.before().differencesFrom(plan.after()),
        plan.after().differencesFrom(plan.before()),
        plan.ticketStillValid(),
        plan.refundInfo(),
        plan.message(),
        SourceType.ORGANIZATION,
        organizationName,
        publishedAt,
        true);
  }

  /** Whether the event in {@code state} still has every value this change set. */
  public boolean isReflectedIn(EventState state) {
    Map<String, Object> current = state.values();
    return newValue.entrySet().stream()
        .allMatch(
            field ->
                current.containsKey(field.getKey())
                    && Objects.equals(current.get(field.getKey()), field.getValue()));
  }
}
