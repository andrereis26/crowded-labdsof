package com.crowded.core.event.api.mapper;

import com.crowded.core.event.api.dto.CrowdResponse;
import com.crowded.core.event.api.dto.ProvenanceResponse;
import com.crowded.core.event.api.dto.QueueTimeResponse;
import com.crowded.core.event.domain.model.CrowdStatus;
import com.crowded.core.event.domain.model.InformationKind;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.QueuePoint;
import com.crowded.core.event.domain.service.FreshnessPolicy;
import java.time.Instant;

/**
 * The freshness rule applied at one moment, so every piece of information in a response is judged
 * at the same time.
 */
public final class FreshnessView {

  private final FreshnessPolicy policy;
  private final Instant now;

  FreshnessView(FreshnessPolicy policy, Instant now) {
    this.policy = policy;
    this.now = now;
  }

  /** The provenance of on-site information of {@code kind}, with whether it is out of date. */
  public ProvenanceResponse onSite(InformationKind kind, Provenance provenance) {
    return new ProvenanceResponse(
        provenance.sourceType(),
        provenance.sourceName(),
        provenance.lastUpdatedAt(),
        provenance.confirmed(),
        policy.isStale(kind, provenance, now),
        policy.staleAt(kind, provenance));
  }

  /** The provenance of an announcement: it never goes out of date. */
  public static ProvenanceResponse announced(Provenance provenance) {
    return new ProvenanceResponse(
        provenance.sourceType(),
        provenance.sourceName(),
        provenance.lastUpdatedAt(),
        provenance.confirmed(),
        false,
        null);
  }

  /** The crowd level with its provenance; null when no level was published. */
  public CrowdResponse crowd(CrowdStatus crowd) {
    if (crowd == null) {
      return null;
    }
    return new CrowdResponse(crowd.level(), onSite(InformationKind.CROWD, crowd.provenance()));
  }

  /** A queue point with its waiting time and provenance; null when no time was published. */
  public QueueTimeResponse queue(QueuePoint queue) {
    if (queue.time() == null) {
      return null;
    }
    return new QueueTimeResponse(
        queue.name(),
        queue.time().waitMinutes(),
        onSite(InformationKind.QUEUE, queue.time().provenance()));
  }
}
