package com.crowded.core.event.application.dto;

import com.crowded.core.event.domain.model.ChangeType;
import com.crowded.core.event.domain.model.EventChange;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.Severity;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * An official change as participants read it: what it changed from and to, what it means for their
 * ticket, and who published it when.
 *
 * @param previousValue the changed fields before it, by their API name
 * @param newValue the same fields after it
 */
public record EventChangeView(
    UUID id,
    ChangeType type,
    Severity severity,
    Map<String, Object> previousValue,
    Map<String, Object> newValue,
    Boolean ticketStillValid,
    String refundInfo,
    String message,
    Provenance provenance) {

  public EventChangeView {
    // Read-only copies that keep the order and the null values (a cleared coordinate).
    previousValue = Collections.unmodifiableMap(new LinkedHashMap<>(previousValue));
    newValue = Collections.unmodifiableMap(new LinkedHashMap<>(newValue));
  }

  public static EventChangeView of(EventChange change) {
    return new EventChangeView(
        change.getId(),
        change.getType(),
        change.getSeverity(),
        change.getPreviousValue(),
        change.getNewValue(),
        change.getTicketStillValid(),
        change.getRefundInfo(),
        change.getMessage(),
        change.getProvenance());
  }
}
