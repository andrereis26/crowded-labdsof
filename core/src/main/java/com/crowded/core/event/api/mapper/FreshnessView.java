package com.crowded.core.event.api.mapper;

import com.crowded.core.event.domain.model.FreshnessPolicy;
import com.crowded.core.event.domain.model.InformationKind;
import com.crowded.core.event.domain.model.Provenance;
import java.time.Instant;

/**
 * The freshness rule at one moment, passed to the mappers as context so every piece of information
 * in a response is judged at the same time.
 */
public final class FreshnessView {

  private final FreshnessPolicy policy;
  private final Instant now;

  FreshnessView(FreshnessPolicy policy, Instant now) {
    this.policy = policy;
    this.now = now;
  }

  /** Whether on-site information of {@code kind} given at {@code provenance} is out of date. */
  public boolean isStale(InformationKind kind, Provenance provenance) {
    return policy.isStale(kind, provenance, now);
  }

  /** When on-site information of {@code kind} given at {@code provenance} goes out of date. */
  public Instant staleAt(InformationKind kind, Provenance provenance) {
    return policy.staleAt(kind, provenance);
  }
}
