package com.crowded.jobs.weather.infrastructure.persistence.mapper;

import com.crowded.jobs.weather.domain.model.Forecast;
import com.crowded.jobs.weather.domain.model.SnapshotKind;
import com.crowded.jobs.weather.domain.model.Warnings;
import com.crowded.jobs.weather.domain.model.WeatherContent;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * A snapshot's content as the JSON of {@code weather_snapshot.content}, which Core reads too:
 * {@code {"warnings": [{"awarenessType", "level", "start", "end", "description"}]}} or {@code
 * {"updatedAt", "days": [{"date", "conditions", "minTemperature", "maxTemperature",
 * "precipitationProbability"}]}}, with ISO-8601 times in UTC.
 */
@Component
public class WeatherContentJson {

  private final JsonMapper json;

  public WeatherContentJson(JsonMapper json) {
    this.json = json;
  }

  public String write(WeatherContent content) {
    return json.writeValueAsString(content);
  }

  public WeatherContent read(SnapshotKind kind, String content) {
    return switch (kind) {
      case WARNINGS -> json.readValue(content, Warnings.class);
      case FORECAST -> json.readValue(content, Forecast.class);
    };
  }
}
