package com.crowded.core.event.domain.model;

/**
 * What an operational alert is about, as the organization chooses it. The type decides how
 * prominently participants see it: an entrance closure is critical, the others are warnings.
 */
public enum AlertType {
  ENTRANCE_CLOSURE(Severity.CRITICAL),
  CAPACITY_REACHED(Severity.WARNING),
  SCHEDULE_DELAY(Severity.WARNING),
  OTHER(Severity.WARNING);

  private final Severity severity;

  AlertType(Severity severity) {
    this.severity = severity;
  }

  /** How prominently participants see an alert of this type. */
  public Severity severity() {
    return severity;
  }
}
