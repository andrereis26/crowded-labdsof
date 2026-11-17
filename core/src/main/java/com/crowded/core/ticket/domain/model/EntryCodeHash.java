package com.crowded.core.ticket.domain.model;

/**
 * The keyed hash of a ticket's entry code for one event, which tells that two imports are the same
 * ticket without keeping the code in clear. It is a credential, so it is never printed.
 */
public record EntryCodeHash(String value) {

  public EntryCodeHash {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException("An entry code hash is not blank");
    }
  }

  @Override
  public String toString() {
    return "EntryCodeHash[<hidden>]";
  }
}
