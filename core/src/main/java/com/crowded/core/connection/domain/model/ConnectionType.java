package com.crowded.core.connection.domain.model;

/** How a participant connected to an event. */
public enum ConnectionType {
  /** With a ticket of a ticketed event. */
  TICKET,
  /** With the access code of a public event, typed or read from its QR code. */
  ACCESS_CODE
}
