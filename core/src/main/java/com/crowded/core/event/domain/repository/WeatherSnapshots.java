package com.crowded.core.event.domain.repository;

import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.WeatherSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** The weather snapshots Jobs stores, read only (ADR 0002). */
public interface WeatherSnapshots {

  /**
   * The latest snapshot of the same district and kind as {@code snapshotId}, so a message that
   * arrives late never brings back older data; empty when there is no such snapshot.
   */
  Optional<WeatherSnapshot> findLatestLike(UUID snapshotId);

  /** The district's latest warnings and latest forecast, those it has. */
  List<WeatherSnapshot> findLatest(District district);
}
