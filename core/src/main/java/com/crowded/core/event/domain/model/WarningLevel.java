package com.crowded.core.event.domain.model;

/**
 * Level of an IPMA weather warning. Orange and red ones are notified, yellow ones only shown; a red
 * one is critical.
 */
public enum WarningLevel {
  YELLOW(Severity.INFO),
  ORANGE(Severity.WARNING),
  RED(Severity.CRITICAL);

  private final Severity severity;

  WarningLevel(Severity severity) {
    this.severity = severity;
  }

  /** How prominently participants see it. */
  public Severity severity() {
    return severity;
  }

  /** Whether participants are notified of it. */
  public boolean isNotified() {
    return this != YELLOW;
  }
}
