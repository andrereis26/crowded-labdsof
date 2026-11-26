package com.crowded.core.event.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Where a piece of information came from and how fresh it is.
 *
 * @param sourceName who gave it, such as the organization's name
 * @param confirmed whether the source confirmed it
 */
public record Provenance(
    SourceType sourceType, String sourceName, Instant lastUpdatedAt, boolean confirmed) {

  public Provenance {
    Objects.requireNonNull(sourceType, "sourceType");
    Objects.requireNonNull(sourceName, "sourceName");
    Objects.requireNonNull(lastUpdatedAt, "lastUpdatedAt");
  }

  /** Confirmed information from the organization named {@code name} at {@code at}. */
  public static Provenance organization(String name, Instant at) {
    return new Provenance(SourceType.ORGANIZATION, name, at, true);
  }

  /** Weather from IPMA, the official source, as of {@code at}. */
  public static Provenance ipma(Instant at) {
    return new Provenance(SourceType.EXTERNAL_SERVICE, "IPMA", at, true);
  }
}
