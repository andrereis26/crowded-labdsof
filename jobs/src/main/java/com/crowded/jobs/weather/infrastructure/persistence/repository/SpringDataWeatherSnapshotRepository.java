package com.crowded.jobs.weather.infrastructure.persistence.repository;

import com.crowded.jobs.weather.domain.model.SnapshotKind;
import com.crowded.jobs.weather.infrastructure.persistence.entity.WeatherSnapshotEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data access to {@code weather_snapshot}. */
interface SpringDataWeatherSnapshotRepository extends JpaRepository<WeatherSnapshotEntity, UUID> {

  Optional<WeatherSnapshotEntity> findFirstByDistrictAndKindOrderByFetchedAtDesc(
      String district, SnapshotKind kind);

  List<WeatherSnapshotEntity> findByPublishedAtIsNullOrderByFetchedAt(Limit limit);
}
