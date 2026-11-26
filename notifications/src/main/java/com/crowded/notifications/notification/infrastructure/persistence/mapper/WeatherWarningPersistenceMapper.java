package com.crowded.notifications.notification.infrastructure.persistence.mapper;

import com.crowded.notifications.notification.domain.model.WarningLevel;
import com.crowded.notifications.notification.domain.model.WeatherWarning;
import com.crowded.notifications.notification.infrastructure.persistence.repository.WeatherWarningRow;
import java.time.ZoneId;
import org.mapstruct.Mapper;

/** Maps the IPMA warnings read from Core's {@code weather_warning}. */
@Mapper
public interface WeatherWarningPersistenceMapper {

  default WeatherWarning toDomain(WeatherWarningRow row) {
    return WeatherWarning.restore(
        row.getId(),
        row.getEventId(),
        row.getAwarenessType(),
        WarningLevel.valueOf(row.getLevel()),
        row.getStartTime(),
        row.getEndTime(),
        row.getDescription(),
        zoneOf(row.getDistrict()));
  }

  /** The legal time of the district, as Core reads it. */
  private static ZoneId zoneOf(String district) {
    return switch (district) {
      case "Região Autónoma dos Açores" -> ZoneId.of("Atlantic/Azores");
      case "Região Autónoma da Madeira" -> ZoneId.of("Atlantic/Madeira");
      default -> ZoneId.of("Europe/Lisbon");
    };
  }
}
