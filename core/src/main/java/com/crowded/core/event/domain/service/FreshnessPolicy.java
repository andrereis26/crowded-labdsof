package com.crowded.core.event.domain.service;

import com.crowded.core.event.domain.model.InformationKind;
import com.crowded.core.event.domain.model.Provenance;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/**
 * Decides when on-site information goes out of date: each kind of information has the time it stays
 * current after its last update.
 */
public final class FreshnessPolicy {

  private final Map<InformationKind, Duration> periods;

  /**
   * @param periods how long each kind stays current; every kind needs a positive period
   * @throws IllegalArgumentException when a kind has no period or a period is not positive
   */
  public FreshnessPolicy(Map<InformationKind, Duration> periods) {
    Map<InformationKind, Duration> copy = new EnumMap<>(InformationKind.class);
    for (InformationKind kind : InformationKind.values()) {
      Duration period = periods.get(kind);
      if (period == null || period.isZero() || period.isNegative()) {
        throw new IllegalArgumentException("Freshness period of " + kind + " must be positive");
      }
      copy.put(kind, period);
    }
    this.periods = copy;
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
