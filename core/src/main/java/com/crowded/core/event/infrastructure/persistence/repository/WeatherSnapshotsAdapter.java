package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.WeatherSnapshot;
import com.crowded.core.event.domain.repository.WeatherSnapshots;
import com.crowded.core.event.infrastructure.persistence.mapper.WeatherSnapshotJson;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link WeatherSnapshots} on Jobs' {@code weather_snapshot} in PostgreSQL. */
@Repository
class WeatherSnapshotsAdapter implements WeatherSnapshots {

  private final SpringDataWeatherSnapshotRepository snapshots;
  private final WeatherSnapshotJson json;

  WeatherSnapshotsAdapter(SpringDataWeatherSnapshotRepository snapshots, WeatherSnapshotJson json) {
    this.snapshots = snapshots;
    this.json = json;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<WeatherSnapshot> findLatestLike(UUID snapshotId) {
    return snapshots.findLatestLike(snapshotId).map(json::toDomain);
  }

  @Override
  @Transactional(readOnly = true)
  public List<WeatherSnapshot> findLatest(District district) {
    return snapshots.findLatestOfEachKind(district.name()).stream().map(json::toDomain).toList();
  }
}
