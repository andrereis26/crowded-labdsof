package com.crowded.jobs.weather.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.crowded.jobs.weather.domain.exception.WeatherUnavailableException;
import com.crowded.jobs.weather.domain.model.Forecast;
import com.crowded.jobs.weather.domain.model.ForecastDay;
import com.crowded.jobs.weather.domain.model.Warning;
import com.crowded.jobs.weather.domain.model.WarningLevel;
import com.crowded.jobs.weather.domain.model.Warnings;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

/** Reads IPMA's open data from a stand-in server that answers what each test sets. */
class IpmaWeatherSourceTest {

  private static final String WARNINGS = "/forecast/warnings/warnings_www.json";
  private static final String PORTO_FORECAST = "/forecast/meteorology/cities/daily/1131200.json";

  private final Map<String, String> answers = new ConcurrentHashMap<>();
  private final AtomicInteger requests = new AtomicInteger();
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  private HttpServer server;
  private IpmaWeatherSource ipma;

  @BeforeEach
  void startIpma() throws IOException {
    server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
    server.createContext(
        "/",
        exchange -> {
          requests.incrementAndGet();
          String answer = answers.get(exchange.getRequestURI().getPath());
          byte[] body = answer == null ? new byte[0] : answer.getBytes(StandardCharsets.UTF_8);
          exchange.sendResponseHeaders(
              answer == null ? 503 : 200, answer == null ? -1 : body.length);
          exchange.getResponseBody().write(body);
          exchange.close();
        });
    server.start();
    ipma =
        new IpmaWeatherSource(
            "http://localhost:" + server.getAddress().getPort(),
            Duration.ofSeconds(2),
            Duration.ofMillis(1),
            JsonMapper.builder().build(),
            meters);
  }

  @AfterEach
  void stopIpma() {
    server.stop(0);
  }

  @Test
  void readsTheYellowOrangeAndRedWarningsOfEachDistrictsArea() {
    answers.put(
        WARNINGS,
        """
        [{"text": "", "awarenessTypeName": "Nevoeiro", "idAreaAviso": "PTO",
          "startTime": "2026-11-27T10:10:00", "awarenessLevelID": "green",
          "endTime": "2026-11-30T10:00:00"},
         {"text": "Rajadas até 90 km/h.", "awarenessTypeName": "Vento", "idAreaAviso": "PTO",
          "startTime": "2026-11-27T18:00:00", "awarenessLevelID": "orange",
          "endTime": "2026-11-28T03:00:00"},
         {"text": "", "awarenessTypeName": "Precipitação", "idAreaAviso": "LSB",
          "startTime": "2026-11-27T12:00:00", "awarenessLevelID": "red",
          "endTime": "2026-11-27T21:00:00"},
         {"text": "", "awarenessTypeName": "Vento", "idAreaAviso": "FAR",
          "startTime": "2026-11-27T12:00:00", "awarenessLevelID": "yellow",
          "endTime": "2026-11-27T21:00:00"}]
        """);

    Map<String, Warnings> warnings = ipma.warnings(Set.of("Porto", "Lisboa", "Braga"));

    assertThat(warnings)
        .containsOnly(
            Map.entry(
                "Porto",
                new Warnings(
                    List.of(
                        new Warning(
                            "Vento",
                            WarningLevel.ORANGE,
                            Instant.parse("2026-11-27T18:00:00Z"),
                            Instant.parse("2026-11-28T03:00:00Z"),
                            "Rajadas até 90 km/h.")))),
            Map.entry(
                "Lisboa",
                new Warnings(
                    List.of(
                        new Warning(
                            "Precipitação",
                            WarningLevel.RED,
                            Instant.parse("2026-11-27T12:00:00Z"),
                            Instant.parse("2026-11-27T21:00:00Z"),
                            null)))),
            Map.entry("Braga", new Warnings(List.of())));
  }

  @Test
  void rejectsOnlyTheDistrictWhoseWarningsAreInvalid() {
    answers.put(
        WARNINGS,
        """
        [{"awarenessTypeName": "Vento", "idAreaAviso": "PTO", "startTime": "amanhã",
          "awarenessLevelID": "orange", "endTime": "2026-11-28T03:00:00"},
         {"awarenessTypeName": "Vento", "idAreaAviso": "LSB", "startTime": "2026-11-27T12:00:00",
          "awarenessLevelID": "purple", "endTime": "2026-11-27T21:00:00"},
         {"awarenessTypeName": "Vento", "idAreaAviso": "FAR", "startTime": "2026-11-27T12:00:00",
          "awarenessLevelID": "yellow", "endTime": "2026-11-27T21:00:00"}]
        """);

    Map<String, Warnings> warnings = ipma.warnings(Set.of("Porto", "Lisboa", "Faro", "Atlântida"));

    assertThat(warnings).containsOnlyKeys("Faro");
    assertThat(meters.counter("crowded.weather.rejected", "data", "warnings").count()).isEqualTo(2);
  }

  @Test
  void rejectsEveryDistrictWhenTheWarningsAreNotAList() {
    answers.put(WARNINGS, "<html>Em manutenção</html>");

    assertThat(ipma.warnings(Set.of("Porto"))).isEmpty();

    answers.put(WARNINGS, "{\"error\": true}");

    assertThat(ipma.warnings(Set.of("Porto"))).isEmpty();
  }

  @Test
  void triesThreeMoreTimesBeforeSayingIpmaIsUnavailable() {
    assertThatThrownBy(() -> ipma.warnings(Set.of("Porto")))
        .isInstanceOf(WeatherUnavailableException.class);
    assertThat(requests.get()).isEqualTo(4);
  }

  @Test
  void readsTheForecastOfTheDistrictsCapitalWithIpmasUpdateTime() {
    answers.put(
        PORTO_FORECAST,
        """
        {"owner": "IPMA", "data": [
          {"precipitaProb": "8.0", "tMin": "10.7", "tMax": "17.1", "idWeatherType": 3,
           "forecastDate": "2026-11-27"},
          {"precipitaProb": "100.0", "tMin": "10.7", "tMax": "14.4", "idWeatherType": 6,
           "forecastDate": "2026-11-28"}],
         "globalIdLocal": 1131200, "dataUpdate": "2026-11-27T09:31:02"}
        """);

    Optional<Forecast> forecast = ipma.forecast("Porto");

    assertThat(forecast)
        .contains(
            new Forecast(
                Instant.parse("2026-11-27T09:31:02Z"),
                List.of(
                    new ForecastDay(
                        LocalDate.parse("2026-11-27"), "Céu parcialmente nublado", 10.7, 17.1, 8),
                    new ForecastDay(
                        LocalDate.parse("2026-11-28"), "Aguaceiros/chuva", 10.7, 14.4, 100))));
  }

  @Test
  void rejectsAnInvalidForecast() {
    answers.put(
        PORTO_FORECAST,
        """
        {"data": [{"precipitaProb": "-99.0", "tMin": "10.7", "tMax": "17.1",
                   "idWeatherType": 3, "forecastDate": "2026-11-27"}],
         "dataUpdate": "2026-11-27T09:31:02"}
        """);

    assertThat(ipma.forecast("Porto")).isEmpty();

    answers.put(PORTO_FORECAST, "{\"data\": [], \"dataUpdate\": \"2026-11-27T09:31:02\"}");

    assertThat(ipma.forecast("Porto")).isEmpty();
    assertThat(meters.counter("crowded.weather.rejected", "data", "forecast").count()).isEqualTo(2);
  }

  @Test
  void hasNoForecastForADistrictIpmaDoesNotKnow() {
    assertThat(ipma.forecast("Atlântida")).isEmpty();
    assertThat(requests.get()).isZero();
  }

  @Test
  void saysIpmaIsUnavailableWhenTheForecastDoesNotArrive() {
    assertThatThrownBy(() -> ipma.forecast("Porto"))
        .isInstanceOf(WeatherUnavailableException.class);
  }
}
