package com.crowded.core.ticket.domain.model;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * What the AI found on a ticket: the event code printed on it and the ticket's details. The code is
 * only a key to look up a registered event, never shown as event information; it is {@code null}
 * when none was found.
 */
public record TicketExtraction(String extractedEventCode, TicketDetails extractedFields) {

  /** Nothing usable was extracted. */
  public static final TicketExtraction NOTHING = new TicketExtraction(null, TicketDetails.NONE);

  /** The only fields the AI may answer with. */
  static final Set<String> SCHEMA = Set.of("eventCode", "gate", "sector", "seat");

  public TicketExtraction {
    extractedEventCode =
        extractedEventCode == null || extractedEventCode.isBlank()
            ? null
            : extractedEventCode.strip();
    extractedFields = extractedFields == null ? TicketDetails.NONE : extractedFields;
  }

  /**
   * The extraction in the AI's answer, or empty when the answer does not follow the fixed schema:
   * only the schema's fields, each text or missing. The answer is untrusted, so anything else is
   * discarded whole and cannot make Core do anything.
   */
  public static Optional<TicketExtraction> fromProviderFields(Map<String, ?> fields) {
    if (fields == null || !SCHEMA.containsAll(fields.keySet())) {
      return Optional.empty();
    }
    for (Object value : fields.values()) {
      if (value != null && !(value instanceof String)) {
        return Optional.empty();
      }
    }
    return Optional.of(
        new TicketExtraction(
            (String) fields.get("eventCode"),
            new TicketDetails(
                (String) fields.get("gate"),
                (String) fields.get("sector"),
                (String) fields.get("seat"))));
  }

  public boolean hasEventCode() {
    return extractedEventCode != null;
  }
}
