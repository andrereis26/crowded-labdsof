package com.crowded.jobs.weather.infrastructure.config;

import com.crowded.jobs.weather.domain.exception.InvalidWeatherDataException;
import com.crowded.jobs.weather.domain.exception.WeatherUnavailableException;
import com.crowded.jobs.weather.domain.model.Forecast;
import com.crowded.jobs.weather.domain.model.ForecastDay;
import com.crowded.jobs.weather.domain.model.Warning;
import com.crowded.jobs.weather.domain.model.WarningLevel;
import com.crowded.jobs.weather.domain.model.Warnings;
import com.crowded.jobs.weather.domain.repository.WeatherSource;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.retry.RetryException;
import org.springframework.core.retry.RetryPolicy;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * IPMA's open data over HTTPS (ADR 0010, Technical Design, IPMA): {@code warnings_www.json} for the
 * warnings of every warning area and {@code cities/daily/{globalIdLocal}.json} for the forecast of
 * a district's capital. Its times are UTC. Each call has a timeout and is tried again after 1 s, 2
 * s and 4 s; what does not parse or validate is rejected and logged, district by district.
 */
final class IpmaWeatherSource implements WeatherSource {

  private static final Logger log = LoggerFactory.getLogger(IpmaWeatherSource.class);

  /**
   * IPMA's warning area and forecast location of each district, from its {@code
   * distrits-islands.json}. A region uses the area of its capital: Ponta Delgada and Funchal.
   */
  private static final Map<String, Location> LOCATIONS =
      Map.ofEntries(
          Map.entry("Aveiro", new Location("AVR", 1010500)),
          Map.entry("Beja", new Location("BJA", 1020500)),
          Map.entry("Braga", new Location("BRG", 1030300)),
          Map.entry("Bragança", new Location("BGC", 1040200)),
          Map.entry("Castelo Branco", new Location("CBO", 1050200)),
          Map.entry("Coimbra", new Location("CBR", 1060300)),
          Map.entry("Évora", new Location("EVR", 1070500)),
          Map.entry("Faro", new Location("FAR", 1080500)),
          Map.entry("Guarda", new Location("GDA", 1090700)),
          Map.entry("Leiria", new Location("LRA", 1100900)),
          Map.entry("Lisboa", new Location("LSB", 1110600)),
          Map.entry("Portalegre", new Location("PTG", 1121400)),
          Map.entry("Porto", new Location("PTO", 1131200)),
          Map.entry("Santarém", new Location("STM", 1141600)),
          Map.entry("Setúbal", new Location("STB", 1151200)),
          Map.entry("Viana do Castelo", new Location("VCT", 1160900)),
          Map.entry("Vila Real", new Location("VRL", 1171400)),
          Map.entry("Viseu", new Location("VIS", 1182300)),
          Map.entry("Região Autónoma dos Açores", new Location("AOR", 3420300)),
          Map.entry("Região Autónoma da Madeira", new Location("MCS", 2310300)));

  /** IPMA's kinds of weather, from its {@code weather-type-classe.json}. */
  private static final Map<Integer, String> WEATHER_TYPES =
      Map.ofEntries(
          Map.entry(0, "Sem informação"),
          Map.entry(1, "Céu limpo"),
          Map.entry(2, "Céu pouco nublado"),
          Map.entry(3, "Céu parcialmente nublado"),
          Map.entry(4, "Céu muito nublado ou encoberto"),
          Map.entry(5, "Céu nublado por nuvens altas"),
          Map.entry(6, "Aguaceiros/chuva"),
          Map.entry(7, "Aguaceiros/chuva fracos"),
          Map.entry(8, "Aguaceiros/chuva fortes"),
          Map.entry(9, "Chuva/aguaceiros"),
          Map.entry(10, "Chuva fraca ou chuvisco"),
          Map.entry(11, "Chuva/aguaceiros forte"),
          Map.entry(12, "Períodos de chuva"),
          Map.entry(13, "Períodos de chuva fraca"),
          Map.entry(14, "Períodos de chuva forte"),
          Map.entry(15, "Chuvisco"),
          Map.entry(16, "Neblina"),
          Map.entry(17, "Nevoeiro ou nuvens baixas"),
          Map.entry(18, "Neve"),
          Map.entry(19, "Trovoada"),
          Map.entry(20, "Aguaceiros e possibilidade de trovoada"),
          Map.entry(21, "Granizo"),
          Map.entry(22, "Geada"),
          Map.entry(23, "Chuva e possibilidade de trovoada"),
          Map.entry(24, "Nebulosidade convectiva"),
          Map.entry(25, "Céu com períodos de muito nublado"),
          Map.entry(26, "Nevoeiro"),
          Map.entry(27, "Céu nublado"),
          Map.entry(28, "Aguaceiros de neve"),
          Map.entry(29, "Chuva e neve"),
          Map.entry(30, "Chuva e neve"));

  private final RestClient client;
  private final RetryTemplate retries;
  private final JsonMapper json;
  private final MeterRegistry meters;

  IpmaWeatherSource(
      String baseUrl,
      Duration timeout,
      Duration firstRetry,
      JsonMapper json,
      MeterRegistry meters) {
    HttpClient http = HttpClient.newBuilder().connectTimeout(timeout).build();
    JdkClientHttpRequestFactory requests = new JdkClientHttpRequestFactory(http);
    requests.setReadTimeout(timeout);
    this.client = RestClient.builder().baseUrl(baseUrl).requestFactory(requests).build();
    this.retries =
        new RetryTemplate(
            RetryPolicy.builder()
                .maxRetries(3)
                .delay(firstRetry)
                .multiplier(2)
                .includes(RestClientException.class)
                .build());
    this.json = json;
    this.meters = meters;
  }

  @Override
  public Map<String, Warnings> warnings(Set<String> districts) {
    JsonNode all = fetch("/forecast/warnings/warnings_www.json");
    Map<String, Warnings> found = new LinkedHashMap<>();
    if (!all.isArray()) {
      for (String district : districts) {
        rejected("warnings", district, new InvalidWeatherDataException("Not a list of warnings"));
      }
      return found;
    }
    for (String district : districts) {
      Location location = LOCATIONS.get(district);
      if (location == null) {
        log.warn("IPMA has no warning area for district {}", district);
        continue;
      }
      try {
        found.put(district, warningsOf(all, location.area()));
      } catch (InvalidWeatherDataException e) {
        rejected("warnings", district, e);
      }
    }
    return found;
  }

  @Override
  public Optional<Forecast> forecast(String district) {
    Location location = LOCATIONS.get(district);
    if (location == null) {
      log.warn("IPMA has no forecast location for district {}", district);
      return Optional.empty();
    }
    JsonNode forecast =
        fetch("/forecast/meteorology/cities/daily/" + location.globalIdLocal() + ".json");
    try {
      return Optional.of(forecastOf(forecast));
    } catch (InvalidWeatherDataException e) {
      rejected("forecast", district, e);
      return Optional.empty();
    }
  }

  /** The area's yellow, orange and red warnings; green means there is none. */
  private static Warnings warningsOf(JsonNode all, String area) {
    List<Warning> warnings = new ArrayList<>();
    for (JsonNode warning : all) {
      if (!area.equals(warning.path("idAreaAviso").asString(""))) {
        continue;
      }
      String level = warning.path("awarenessLevelID").asString("");
      if (level.equals("green")) {
        continue;
      }
      warnings.add(
          new Warning(
              warning.path("awarenessTypeName").asString(null),
              levelOf(level),
              instantOf(warning.path("startTime")),
              instantOf(warning.path("endTime")),
              warning.path("text").asString(null)));
    }
    return new Warnings(warnings);
  }

  private static Forecast forecastOf(JsonNode forecast) {
    List<ForecastDay> days = new ArrayList<>();
    for (JsonNode day : forecast.path("data")) {
      days.add(
          new ForecastDay(
              dateOf(day.path("forecastDate")),
              WEATHER_TYPES.get(day.path("idWeatherType").asInt(-99)),
              numberOf(day.path("tMin")),
              numberOf(day.path("tMax")),
              numberOf(day.path("precipitaProb"))));
    }
    return new Forecast(instantOf(forecast.path("dataUpdate")), days);
  }

  private static WarningLevel levelOf(String level) {
    return switch (level) {
      case "yellow" -> WarningLevel.YELLOW;
      case "orange" -> WarningLevel.ORANGE;
      case "red" -> WarningLevel.RED;
      default -> throw new InvalidWeatherDataException("Unknown warning level " + level);
    };
  }

  private static Instant instantOf(JsonNode value) {
    try {
      return LocalDateTime.parse(value.asString("")).toInstant(ZoneOffset.UTC);
    } catch (DateTimeParseException e) {
      throw new InvalidWeatherDataException("Invalid time " + value, e);
    }
  }

  private static LocalDate dateOf(JsonNode value) {
    try {
      return LocalDate.parse(value.asString(""));
    } catch (DateTimeParseException e) {
      throw new InvalidWeatherDataException("Invalid date " + value, e);
    }
  }

  /** IPMA sends numbers as text, such as {@code "17.1"}. */
  private static double numberOf(JsonNode value) {
    try {
      return Double.parseDouble(value.asString(""));
    } catch (NumberFormatException e) {
      throw new InvalidWeatherDataException("Invalid number " + value, e);
    }
  }

  /** The document at {@code path}, read as JSON; any JSON it is, even one that does not fit. */
  private JsonNode fetch(String path) {
    // Bytes, so the JSON is read as UTF-8 whatever charset the response declares.
    byte[] body;
    try {
      body = retries.execute(() -> client.get().uri(path).retrieve().body(byte[].class));
    } catch (RetryException e) {
      throw new WeatherUnavailableException("IPMA did not answer " + path, e.getCause());
    }
    try {
      return body == null ? json.missingNode() : json.readTree(body);
    } catch (JacksonException e) {
      log.warn("IPMA answered {} with something that is not JSON", path);
      return json.missingNode();
    }
  }

  private void rejected(String what, String district, InvalidWeatherDataException e) {
    Counter.builder("crowded.weather.rejected")
        .description("IPMA data rejected as invalid, by district")
        .tag("data", what)
        .register(meters)
        .increment();
    log.warn("Rejected IPMA {} for {}: {}", what, district, e.getMessage());
  }

  /**
   * Where IPMA has a district's data.
   *
   * @param area the warning area, {@code idAreaAviso}
   * @param globalIdLocal the forecast location
   */
  private record Location(String area, int globalIdLocal) {}
}
