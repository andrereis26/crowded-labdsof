package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.Objects;

/**
 * How participants get into an event: a ticketed event's code is the one printed on its tickets, a
 * public event's is the access code its organization gives out.
 */
public record Admission(EventType type, EventCode code) {

  /**
   * @throws RuleViolationException when the type is missing
   */
  public Admission {
    if (type == null) {
      throw new RuleViolationException("TYPE_REQUIRED", "Choose a ticketed or a public event.");
    }
    Objects.requireNonNull(code, "code");
  }

  /** The same type of admission with another code. */
  public Admission withCode(EventCode newCode) {
    return new Admission(type, newCode);
  }

  /** The code printed on the tickets; null for a public event. */
  public String ticketEventCode() {
    return type == EventType.TICKETED ? code.value() : null;
  }

  /** The code the organization gives out; null for a ticketed event. */
  public String accessCode() {
    return type == EventType.PUBLIC ? code.value() : null;
  }
}
