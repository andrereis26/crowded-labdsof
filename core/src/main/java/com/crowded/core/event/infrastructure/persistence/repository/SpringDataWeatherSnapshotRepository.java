package com.crowded.core.event.infrastructure.persistence.repository;

import com.crowded.core.event.infrastructure.persistence.entity.WeatherSnapshotEntity;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to Jobs' {@code weather_snapshot}. */
interface SpringDataWeatherSnapshotRepository extends Repository<WeatherSnapshotEntity, UUID> {

  /** The latest snapshot of the same district and kind as {@code id}. */
  @Query(
      value =
          """
          SELECT latest.* FROM weather_snapshot latest
          JOIN weather_snapshot given
            ON given.id = :id AND latest.district = given.district AND latest.kind = given.kind
          ORDER BY latest.fetched_at DESC LIMIT 1""",
      nativeQuery = true)
  Optional<WeatherSnapshotEntity> findLatestLike(@Param("id") UUID id);

  /** The district's latest snapshot of each kind. */
  @Query(
      value =
          """
          SELECT DISTINCT ON (kind) * FROM weather_snapshot
          WHERE district = :district ORDER BY kind, fetched_at DESC""",
      nativeQuery = true)
  List<WeatherSnapshotEntity> findLatestOfEachKind(@Param("district") String district);
}
