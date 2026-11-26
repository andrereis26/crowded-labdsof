package com.crowded.jobs.weather.domain.repository;

import com.crowded.jobs.weather.domain.model.SnapshotKind;
import com.crowded.jobs.weather.domain.model.WeatherSnapshot;
import java.util.List;
import java.util.Optional;

/** The weather snapshots Jobs keeps. */
public interface WeatherSnapshotRepository {

  /** The district's latest snapshot of that kind; empty before the first one. */
  Optional<WeatherSnapshot> findLatest(String district, SnapshotKind kind);

  /** Stores a new snapshot. */
  void add(WeatherSnapshot snapshot);

  /** Up to {@code limit} snapshots whose message is not confirmed yet, the oldest first. */
  List<WeatherSnapshot> findUnpublished(int limit);

  /** Stores that the snapshot's message was confirmed. */
  void save(WeatherSnapshot snapshot);
}
