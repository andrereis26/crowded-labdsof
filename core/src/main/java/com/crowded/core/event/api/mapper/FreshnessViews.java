package com.crowded.core.event.api.mapper;

import com.crowded.core.event.domain.model.FreshnessPolicy;
import java.time.Clock;
import org.springframework.stereotype.Component;

/** Gives the freshness rule at the current time, for the response about to be built. */
@Component
public class FreshnessViews {

  private final FreshnessPolicy policy;
  private final Clock clock;

  FreshnessViews(FreshnessPolicy policy, Clock clock) {
    this.policy = policy;
    this.clock = clock;
  }

  public FreshnessView now() {
    return new FreshnessView(policy, clock.instant());
  }
}
