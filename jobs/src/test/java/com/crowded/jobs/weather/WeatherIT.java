package com.crowded.jobs.weather;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.jobs.IntegrationTest;
import com.crowded.jobs.weather.application.service.WeatherWatch;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * The weather watch against PostgreSQL, RabbitMQ and a stand-in for IPMA: what changed for a
 * district with upcoming events (the demo events are in Porto) is stored once and announced to
 * Core.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class WeatherIT extends IntegrationTest {

  private static final String WARNINGS = "/forecast/warnings/warnings_www.json";
  private static final String PORTO_FORECAST = "/forecast/meteorology/cities/daily/1131200.json";

  @Autowired private WeatherWatch watch;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private RabbitTemplate rabbit;
  @Autowired private RabbitAdmin admin;
  @Autowired private TopicExchange crowdedEvents;

  private Queue core;

  @BeforeEach
  void listenAsCore() {
    // Not exclusive: the template may receive on another connection than the admin's.
    core = new Queue("weather-it-" + UUID.randomUUID(), false, false, false);
    admin.declareQueue(core);
    admin.declareBinding(BindingBuilder.bind(core).to(crowdedEvents).with("weather.updated"));
  }

  @AfterEach
  void stopListening() {
    admin.deleteQueue(core.getName());
    IPMA_ANSWERS.clear();
  }

  @Test
  void storesAndAnnouncesANewWarningOnceAndTheForecastOfPorto() {
    IPMA_ANSWERS.put(
        WARNINGS,
        """
        [{"text": "Rajadas até 90 km/h.", "awarenessTypeName": "Vento", "idAreaAviso": "PTO",
          "startTime": "2030-01-10T18:00:00", "awarenessLevelID": "orange",
          "endTime": "2030-01-11T03:00:00"}]
        """);
    IPMA_ANSWERS.put(
        PORTO_FORECAST,
        """
        {"data": [{"precipitaProb": "8.0", "tMin": "10.7", "tMax": "17.1", "idWeatherType": 3,
                   "forecastDate": "2030-01-10"}], "dataUpdate": "2030-01-10T09:31:02"}
        """);

    watch.check();
    watch.check();

    Map<String, Object> warnings = latest("WARNINGS");
    assertThat(warnings.get("content").toString())
        .contains("\"awarenessType\": \"Vento\"", "\"level\": \"ORANGE\"", "Rajadas até 90 km/h.");
    assertThat(warnings.get("published_at")).isNotNull();
    assertThat(latest("FORECAST").get("content").toString())
        .contains("\"conditions\": \"Céu parcialmente nublado\"");

    // One message per snapshot, in no particular order: both were fetched in the same run.
    assertThat(List.of(received(), received()))
        .allMatch(message -> message.contains("\"district\":\"Porto\""))
        .anyMatch(message -> message.contains(warnings.get("id").toString()));
    assertThat(rabbit.receive(core.getName(), 500)).isNull();
  }

  @Test
  void storesNothingWhenIpmaIsUnavailable() {
    long before = count();

    watch.check();

    assertThat(count()).isEqualTo(before);
  }

  private Map<String, Object> latest(String kind) {
    return jdbc.queryForMap(
        "SELECT id, content::text AS content, published_at FROM weather_snapshot"
            + " WHERE district = 'Porto' AND kind = CAST(? AS weather_snapshot_kind)"
            + " ORDER BY fetched_at DESC LIMIT 1",
        kind);
  }

  private long count() {
    return jdbc.queryForObject("SELECT count(*) FROM weather_snapshot", Long.class);
  }

  private String received() {
    Message message = rabbit.receive(core.getName(), 5000);
    assertThat(message).isNotNull();
    return new String(message.getBody(), StandardCharsets.UTF_8);
  }
}
