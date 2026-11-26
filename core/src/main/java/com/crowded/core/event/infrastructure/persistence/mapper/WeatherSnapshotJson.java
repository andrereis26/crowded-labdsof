package com.crowded.core.event.infrastructure.persistence.mapper;

import com.crowded.core.event.domain.model.District;
import com.crowded.core.event.domain.model.ForecastSnapshot;
import com.crowded.core.event.domain.model.IssuedWarning;
import com.crowded.core.event.domain.model.Provenance;
import com.crowded.core.event.domain.model.WarningLevel;
import com.crowded.core.event.domain.model.WarningsSnapshot;
import com.crowded.core.event.domain.model.WeatherForecast;
import com.crowded.core.event.domain.model.WeatherSnapshot;
import com.crowded.core.event.infrastructure.persistence.entity.WeatherSnapshotEntity;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reads the JSON Jobs writes in {@code weather_snapshot.content} (Jobs' {@code
 * WeatherContentJson}): {@code {"warnings": [...]}} or {@code {"updatedAt", "days": [...]}}.
 */
@Component
public class WeatherSnapshotJson {

  private final JsonMapper json;

  public WeatherSnapshotJson(JsonMapper json) {
    this.json = json;
  }

  public WeatherSnapshot toDomain(WeatherSnapshotEntity entity) {
    JsonNode content = json.readTree(entity.getContent());
    District district = new District(entity.getDistrict());
    return switch (entity.getKind()) {
      case "WARNINGS" -> new WarningsSnapshot(district, entity.getFetchedAt(), warnings(content));
      case "FORECAST" -> new ForecastSnapshot(district, forecast(content));
      default -> throw new IllegalStateException("Unknown weather snapshot " + entity.getKind());
    };
  }

  private static List<IssuedWarning> warnings(JsonNode content) {
    List<IssuedWarning> warnings = new ArrayList<>();
    for (JsonNode warning : content.path("warnings")) {
      warnings.add(
          new IssuedWarning(
              warning.path("awarenessType").asString(),
              WarningLevel.valueOf(warning.path("level").asString()),
              Instant.parse(warning.path("start").asString()),
              Instant.parse(warning.path("end").asString()),
              warning.path("description").asString(null)));
    }
    return warnings;
  }

  private static List<WeatherForecast> forecast(JsonNode content) {
    Provenance ipma = Provenance.ipma(Instant.parse(content.path("updatedAt").asString()));
    List<WeatherForecast> days = new ArrayList<>();
    for (JsonNode day : content.path("days")) {
      days.add(
          new WeatherForecast(
              LocalDate.parse(day.path("date").asString()),
              day.path("conditions").asString(),
              day.path("minTemperature").asDouble(),
              day.path("maxTemperature").asDouble(),
              day.path("precipitationProbability").asDouble(),
              ipma));
    }
    return days;
  }
}
