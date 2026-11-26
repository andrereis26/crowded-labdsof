package com.crowded.core.event.infrastructure.persistence.mapper;

import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.WeatherForecast;
import com.crowded.core.event.domain.model.WeatherWarning;
import com.crowded.core.event.infrastructure.persistence.entity.WeatherForecastEntity;
import com.crowded.core.event.infrastructure.persistence.entity.WeatherWarningEntity;
import java.util.UUID;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

/**
 * Maps an event's forecast and warnings to {@code weather_forecast} and {@code weather_warning}.
 */
@Mapper
public interface WeatherPersistenceMapper {

  @Mapping(target = "provenanceSourceType", source = "forecast.provenance.sourceType")
  @Mapping(target = "provenanceSourceName", source = "forecast.provenance.sourceName")
  @Mapping(target = "provenanceLastUpdatedAt", source = "forecast.provenance.lastUpdatedAt")
  @Mapping(target = "provenanceConfirmed", source = "forecast.provenance.confirmed")
  WeatherForecastEntity toEntity(UUID id, UUID eventId, WeatherForecast forecast);

  @Mapping(target = "startTime", source = "start")
  @Mapping(target = "endTime", source = "end")
  @Mapping(target = "provenanceSourceType", source = "provenance.sourceType")
  @Mapping(target = "provenanceSourceName", source = "provenance.sourceName")
  @Mapping(target = "provenanceLastUpdatedAt", source = "provenance.lastUpdatedAt")
  @Mapping(target = "provenanceConfirmed", source = "provenance.confirmed")
  WeatherWarningEntity toEntity(WeatherWarning warning);

  default WeatherForecast toDomain(WeatherForecastEntity entity) {
    return new WeatherForecast(
        entity.getDate(),
        entity.getConditions(),
        entity.getMinTemperature(),
        entity.getMaxTemperature(),
        entity.getPrecipitationProbability(),
        new Provenance(
            entity.getProvenanceSourceType(),
            entity.getProvenanceSourceName(),
            entity.getProvenanceLastUpdatedAt(),
            entity.isProvenanceConfirmed()));
  }

  default WeatherWarning toDomain(WeatherWarningEntity entity) {
    return WeatherWarning.restore(
        entity.getId(),
        entity.getEventId(),
        entity.getAwarenessType(),
        entity.getStartTime(),
        entity.getEndTime(),
        entity.getLevel(),
        entity.getNotifiedLevel(),
        entity.getDescription(),
        new Provenance(
            entity.getProvenanceSourceType(),
            entity.getProvenanceSourceName(),
            entity.getProvenanceLastUpdatedAt(),
            entity.isProvenanceConfirmed()));
  }
}
