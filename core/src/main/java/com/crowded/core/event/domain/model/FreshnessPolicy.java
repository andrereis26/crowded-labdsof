package com.crowded.core.event.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * How long each kind of on-site information stays current after its last update, and so when it
 * goes out of date.
 *
 * @param periods how long each kind stays current; every kind needs a positive period
 */
public record FreshnessPolicy(Map<InformationKind, Duration> periods) {

  /**
   * @throws IllegalArgumentException when a kind has no period or a period is not positive
   */
  public FreshnessPolicy {
    Objects.requireNonNull(periods, "periods");
    for (InformationKind kind : InformationKind.values()) {
      Duration period = periods.get(kind);
      if (period == null || period.isZero() || period.isNegative()) {
        throw new IllegalArgumentException("Freshness period of " + kind + " must be positive");
      }
    }
    periods = Map.copyOf(periods);
  }

  /** The moment the information given at {@code provenance} stops being current. */
  public Instant staleAt(InformationKind kind, Provenance provenance) {
    Objects.requireNonNull(kind, "kind");
    return provenance.lastUpdatedAt().plus(periods.get(kind));
  }

  /** Whether the information is out of date at {@code now}: from the moment its period ends. */
  public boolean isStale(InformationKind kind, Provenance provenance, Instant now) {
    return !now.isBefore(staleAt(kind, provenance));
  }
}
