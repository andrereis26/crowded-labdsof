package com.crowded.core.event.api.dto;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.Severity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An official change in {@code GET /events/{eventId}/changes} and the event page.
 *
 * @param previousValue the changed fields before it, by their API name
 * @param newValue the same fields after it
 * @param ticketStillValid for a postponement or cancellation; null otherwise
 */
public record EventChangeResponse(
    UUID id,
    ChangeType type,
    Severity severity,
    Map<String, Object> previousValue,
    Map<String, Object> newValue,
    Boolean ticketStillValid,
    String refundInfo,
    String message,
    ProvenanceResponse provenance) {

  public EventChangeResponse {
    // Read-only copies that keep the order and the null values (a cleared coordinate).
    previousValue = Collections.unmodifiableMap(new LinkedHashMap<>(previousValue));
    newValue = Collections.unmodifiableMap(new LinkedHashMap<>(newValue));
  }
}
