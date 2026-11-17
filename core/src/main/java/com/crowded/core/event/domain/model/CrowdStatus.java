package com.crowded.core.event.domain.model;

import com.crowded.core.shared.domain.exception.RuleViolationException;
import java.util.Objects;

/** The crowd level participants read on an event's page, with who gave it and when. */
public record CrowdStatus(CrowdLevel level, Provenance provenance) {

  /**
   * @throws RuleViolationException when the level is missing
   */
  public CrowdStatus {
    if (level == null) {
      throw new RuleViolationException(
          "CROWD_LEVEL_REQUIRED", "Choose the crowd level: low, moderate, high or full.");
    }
    Objects.requireNonNull(provenance, "provenance");
  }
}
