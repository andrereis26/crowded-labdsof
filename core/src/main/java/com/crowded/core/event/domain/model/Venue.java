package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;

/**
 * Where an event takes place.
 *
 * @param name the venue's name, stripped
 * @param address its address, stripped
 * @param coordinates where it is on the map, or null when unknown
 */
public record Venue(String name, String address, Coordinates coordinates) {

  /**
   * @throws RuleViolationException when the name or the address is blank
   */
  public Venue {
    name = stripped(name);
    address = stripped(address);
    if (name == null || address == null) {
      throw new RuleViolationException("VENUE_REQUIRED", "A venue needs its name and its address.");
    }
  }

  /** Whether {@code other} is the same place: same name and address, and same coordinates. */
  boolean isSamePlaceAs(Venue other) {
    return name.equals(other.name)
        && address.equals(other.address)
        && (coordinates == null || coordinates.equals(other.coordinates));
  }

  private static String stripped(String value) {
    return value == null || value.isBlank() ? null : value.strip();
  }
}
