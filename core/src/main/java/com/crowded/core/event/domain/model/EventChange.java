package com.crowded.core.event.domain.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * An official change to an event, with the fields it changed before and after it. Only {@link
 * Event#publishChange} creates one; it never changes afterwards.
 */
public final class EventChange {

  private final UUID id;
  private final UUID eventId;
  private final ChangeType type;
  private final Severity severity;
  private final Map<String, Object> previousValue;
  private final Map<String, Object> newValue;
  private final Boolean ticketStillValid;
  private final String refundInfo;
  private final String message;
  private final Provenance provenance;

  private EventChange(
      UUID id,
      UUID eventId,
      ChangeType type,
      Severity severity,
      Map<String, Object> previousValue,
      Map<String, Object> newValue,
      Boolean ticketStillValid,
      String refundInfo,
      String message,
      Provenance provenance) {
    this.id = Objects.requireNonNull(id, "id");
    this.eventId = Objects.requireNonNull(eventId, "eventId");
    this.type = Objects.requireNonNull(type, "type");
    this.severity = Objects.requireNonNull(severity, "severity");
    // Copies that keep the order and allow null values (a cleared coordinate).
    this.previousValue = Collections.unmodifiableMap(new LinkedHashMap<>(previousValue));
    this.newValue = Collections.unmodifiableMap(new LinkedHashMap<>(newValue));
    this.ticketStillValid = ticketStillValid;
    this.refundInfo = refundInfo;
    this.message = message;
    this.provenance = Objects.requireNonNull(provenance, "provenance");
  }

  /** The change {@code plan} to event {@code eventId}, published by {@code provenance}. */
  static EventChange publish(UUID eventId, ChangePlan plan, Provenance provenance) {
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
        provenance);
  }

  /** A stored change, as it was saved. */
  public static EventChange restore(
      UUID id,
      UUID eventId,
      ChangeType type,
      Severity severity,
      Map<String, Object> previousValue,
      Map<String, Object> newValue,
      Boolean ticketStillValid,
      String refundInfo,
      String message,
      Provenance provenance) {
    return new EventChange(
        id,
        eventId,
        type,
        severity,
        previousValue,
        newValue,
        ticketStillValid,
        refundInfo,
        message,
        provenance);
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

  public UUID getId() {
    return id;
  }

  public UUID getEventId() {
    return eventId;
  }

  public ChangeType getType() {
    return type;
  }

  public Severity getSeverity() {
    return severity;
  }

  /** The changed fields' values before the change; read-only. */
  public Map<String, Object> getPreviousValue() {
    return previousValue;
  }

  /** The changed fields' values after it; read-only. */
  public Map<String, Object> getNewValue() {
    return newValue;
  }

  public Boolean getTicketStillValid() {
    return ticketStillValid;
  }

  public String getRefundInfo() {
    return refundInfo;
  }

  public String getMessage() {
    return message;
  }

  public Provenance getProvenance() {
    return provenance;
  }

  @Override
  public boolean equals(Object other) {
    return other instanceof EventChange change && id.equals(change.id);
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }

  @Override
  public String toString() {
    return "EventChange[" + id + ", " + type + " of event " + eventId + "]";
  }
}
