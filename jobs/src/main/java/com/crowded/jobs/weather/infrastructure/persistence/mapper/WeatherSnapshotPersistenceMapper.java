package com.crowded.jobs.weather.infrastructure.persistence.mapper;

import com.crowded.jobs.weather.domain.model.WeatherSnapshot;
import com.crowded.jobs.weather.infrastructure.persistence.entity.WeatherSnapshotEntity;
import org.mapstruct.Context;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/** Maps weather snapshots to and from {@code weather_snapshot}. */
@Mapper
public interface WeatherSnapshotPersistenceMapper {

  @Mapping(target = "kind", expression = "java(snapshot.kind())")
  @Mapping(target = "content", expression = "java(json.write(snapshot.getContent()))")
  WeatherSnapshotEntity toEntity(WeatherSnapshot snapshot, @Context WeatherContentJson json);

  default WeatherSnapshot toDomain(WeatherSnapshotEntity entity, WeatherContentJson json) {
    return WeatherSnapshot.restore(
        entity.getId(),
        entity.getDistrict(),
        json.read(entity.getKind(), entity.getContent()),
        entity.getFetchedAt(),
        entity.getPublishedAt());
  }
}
