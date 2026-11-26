package com.crowded.jobs.weather.infrastructure.persistence.repository;

import com.crowded.jobs.weather.domain.model.SnapshotKind;
import com.crowded.jobs.weather.domain.model.WeatherSnapshot;
import com.crowded.jobs.weather.domain.repository.WeatherSnapshotRepository;
import com.crowded.jobs.weather.infrastructure.persistence.mapper.WeatherContentJson;
import com.crowded.jobs.weather.infrastructure.persistence.mapper.WeatherSnapshotPersistenceMapper;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/** {@link WeatherSnapshotRepository} on {@code weather_snapshot} in PostgreSQL. */
@Repository
class WeatherSnapshotRepositoryAdapter implements WeatherSnapshotRepository {

  private final SpringDataWeatherSnapshotRepository snapshots;
  private final WeatherSnapshotPersistenceMapper mapper;
  private final WeatherContentJson json;

  WeatherSnapshotRepositoryAdapter(
      SpringDataWeatherSnapshotRepository snapshots,
      WeatherSnapshotPersistenceMapper mapper,
      WeatherContentJson json) {
    this.snapshots = snapshots;
    this.mapper = mapper;
    this.json = json;
  }

  @Override
  @Transactional(readOnly = true)
  public Optional<WeatherSnapshot> findLatest(String district, SnapshotKind kind) {
    return snapshots
        .findFirstByDistrictAndKindOrderByFetchedAtDesc(district, kind)
        .map(entity -> mapper.toDomain(entity, json));
  }

  @Override
  @Transactional
  public void add(WeatherSnapshot snapshot) {
    snapshots.save(mapper.toEntity(snapshot, json));
  }

  @Override
  @Transactional(readOnly = true)
  public List<WeatherSnapshot> findUnpublished(int limit) {
    return snapshots.findByPublishedAtIsNullOrderByFetchedAt(Limit.of(limit)).stream()
        .map(entity -> mapper.toDomain(entity, json))
        .toList();
  }

  @Override
  @Transactional
  public void save(WeatherSnapshot snapshot) {
    snapshots.save(mapper.toEntity(snapshot, json));
  }
}
