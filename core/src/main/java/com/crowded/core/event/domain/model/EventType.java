package com.crowded.core.event.domain.model;

/** How participants connect to an event. */
public enum EventType {
  /** With a ticket bought elsewhere, matched by the event code printed on it. */
  TICKETED,
  /** With the access code the organization gives out, typed or read from its QR code. */
  PUBLIC
}
