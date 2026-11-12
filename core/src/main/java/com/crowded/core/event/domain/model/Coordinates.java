package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;

/** A point on the map, in degrees (WGS 84). */
public record Coordinates(double latitude, double longitude) {

  /**
   * @throws RuleViolationException when the latitude is outside [-90, 90] or the longitude outside
   *     [-180, 180]
   */
  public Coordinates {
    if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
      throw new RuleViolationException(
          "COORDINATES_OUT_OF_RANGE",
          "The latitude must be between -90 and 90, and the longitude between -180 and 180.");
    }
  }

  /**
   * The coordinates of {@code latitude} and {@code longitude}, or null when both are null.
   *
   * @throws RuleViolationException when only one of them is given
   */
  public static Coordinates ofNullable(Double latitude, Double longitude) {
    if (latitude == null && longitude == null) {
      return null;
    }
    if (latitude == null || longitude == null) {
      throw new RuleViolationException(
          "COORDINATES_INCOMPLETE", "Send both latitude and longitude, or neither.");
    }
    return new Coordinates(latitude, longitude);
  }
}
