package com.crowded.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Jobs announces IPMA's weather for a district: Core stores the forecast and the warnings during
 * the district's events, notifies orange and red ones once per level through the outbox, and shows
 * them on the event's page. A new event takes the district's latest weather at once, and a message
 * about a snapshot Jobs does not have ends in Core's dead-letter queue.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class WeatherIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final String PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);
  // No demo event is in Beja, so only this test's event takes the weather.
  private static final String DISTRICT = "Beja";
  private static final Instant START =
      Instant.now().plus(1, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  private static final Instant END = START.plus(3, ChronoUnit.HOURS);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AmqpAdmin amqp;
  @Autowired private RabbitTemplate rabbit;
  @Autowired private JsonMapper json;

  private final JdbcTemplate jobs = asOwner();
  private final UUID organization = UUID.randomUUID();
  private final UUID participant = UUID.randomUUID();
  private final UUID member = UUID.randomUUID();
  private final UUID event = UUID.randomUUID();
  private final String queue = "weather-it-" + UUID.randomUUID();

  @BeforeEach
  void createAnEventInBejaWithAConnectedParticipant() {
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now())",
        organization,
        "Alentejo Vivo " + organization);
    jdbc.update(
        "INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at, active, name)"
            + " VALUES (?, NULL, ?, ?, 'PARTICIPANT', now(), true, 'Inês'),"
            + " (?, ?, ?, ?, 'ORGANIZATION_MEMBER', now(), true, 'Rui')",
        participant,
        participant + "@crowded.test",
        PASSWORD_HASH,
        member,
        organization,
        member + "@crowded.test",
        PASSWORD_HASH);
    jdbc.update(
        "INSERT INTO event (id, organization_id, name, description, type, ticket_event_code,"
            + " start_date_time, end_date_time, status, venue_name, address, district, latitude,"
            + " longitude)"
            + " VALUES (?, ?, 'Festa', 'Teste', 'TICKETED', ?, ?, ?, 'SCHEDULED',"
            + " 'Castelo', 'Beja', ?, 38.015, -7.863)",
        event,
        organization,
        "WX-" + event,
        Timestamp.from(START),
        Timestamp.from(END),
        DISTRICT);
    jdbc.update(
        "INSERT INTO event_connection (id, user_id, event_id, connected_at) VALUES (?, ?, ?, now())",
        UUID.randomUUID(),
        participant,
        event);
    TopicExchange exchange = new TopicExchange("crowded.events", true, false);
    amqp.declareExchange(exchange);
    Queue listener = new Queue(queue, false, false, false);
    amqp.declareQueue(listener);
    amqp.declareBinding(BindingBuilder.bind(listener).to(exchange).with("notification.requested"));
  }

  @AfterEach
  void removeTheQueueAndTheSnapshots() {
    amqp.deleteQueue(queue);
    jobs.update("DELETE FROM weather_snapshot WHERE district = ?", DISTRICT);
  }

  @Test
  void storesNotifiesOncePerLevelAndShowsTheWeather() throws Exception {
    String day = START.atZone(ZoneId.of("Europe/Lisbon")).toLocalDate().toString();
    UUID forecast = snapshot("FORECAST", forecast(day));
    UUID warnings =
        snapshot(
            "WARNINGS",
            warnings(
                warning("Vento", "ORANGE", START.minusSeconds(3600), END),
                warning("Nevoeiro", "YELLOW", START, END),
                warning("Precipitação", "RED", END.plusSeconds(3600), END.plusSeconds(7200))));

    announce(forecast);
    announce(warnings);
    waitFor("SELECT count(*) FROM weather_warning WHERE event_id = ?", 2);
    waitFor("SELECT count(*) FROM weather_forecast WHERE event_id = ?", 1);

    UUID wind =
        jdbc.queryForObject(
            "SELECT id FROM weather_warning WHERE event_id = ? AND awareness_type = 'Vento'",
            UUID.class,
            event);
    assertThat(
            jdbc.queryForMap(
                "SELECT level::text, notified_level::text, provenance_source_name"
                    + " FROM weather_warning WHERE id = ?",
                wind))
        .containsEntry("level", "ORANGE")
        .containsEntry("notified_level", "ORANGE")
        .containsEntry("provenance_source_name", "IPMA");
    assertThat(
            jdbc.queryForObject(
                "SELECT notified_level::text FROM weather_warning"
                    + " WHERE event_id = ? AND awareness_type = 'Nevoeiro'",
                String.class,
                event))
        .isNull();
    JsonNode request = json.readTree(receiveFor(wind, 10_000).getBody());
    assertThat(request.get("type").asString()).isEqualTo("WEATHER_WARNING");
    assertThat(request.get("eventId").asString()).isEqualTo(event.toString());

    JsonNode weather = json.readTree(page(participant).body()).get("weather");
    assertThat(weather.get("forecast").get(0).get("date").asString()).isEqualTo(day);
    assertThat(weather.get("forecast").get(0).get("conditions").asString()).isEqualTo("Céu limpo");
    assertThat(weather.get("forecast").get(0).get("provenance").get("lastUpdatedAt").asString())
        .isEqualTo("2026-11-27T09:31:02Z");
    assertThat(weather.get("warnings").size()).isEqualTo(2);
    assertThat(weather.get("warnings").get(0).get("level").asString()).isEqualTo("ORANGE");
    assertThat(weather.get("warnings").get(0).get("severity").asString()).isEqualTo("WARNING");
    assertThat(weather.get("warnings").get(1).get("level").asString()).isEqualTo("YELLOW");

    // The same message again notifies no one.
    announce(warnings);
    assertThat(receiveFor(wind, 3_000)).isNull();

    // IPMA raises the wind to red: a new snapshot, and a late message of the old one applies it.
    snapshot("WARNINGS", warnings(warning("Vento", "RED", START.minusSeconds(3600), END)));
    announce(warnings);
    assertThat(json.readTree(receiveFor(wind, 10_000).getBody()).get("type").asString())
        .isEqualTo("WEATHER_WARNING");
    waitFor("SELECT count(*) FROM weather_warning WHERE event_id = ?", 1);
    assertThat(
            jdbc.queryForObject(
                "SELECT notified_level::text FROM weather_warning WHERE id = ?",
                String.class,
                wind))
        .isEqualTo("RED");
    JsonNode red = json.readTree(page(participant).body()).get("weather").get("warnings").get(0);
    assertThat(red.get("severity").asString()).isEqualTo("CRITICAL");
  }

  @Test
  void aNewEventTakesItsDistrictsLatestWeatherAtOnce() throws Exception {
    String day = START.atZone(ZoneId.of("Europe/Lisbon")).toLocalDate().toString();
    snapshot("FORECAST", forecast(day));
    snapshot("WARNINGS", warnings(warning("Vento", "ORANGE", START.minusSeconds(3600), END)));

    HttpResponse<String> created = register();

    assertThat(created.statusCode()).isEqualTo(201);
    UUID registered = UUID.fromString(json.readTree(created.body()).get("id").asString());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM weather_forecast WHERE event_id = ? AND date = CAST(? AS date)",
                Integer.class,
                registered,
                day))
        .isOne();
    assertThat(
            jdbc.queryForMap(
                "SELECT level::text, notified_level::text FROM weather_warning WHERE event_id = ?",
                registered))
        .containsEntry("level", "ORANGE")
        .containsEntry("notified_level", "ORANGE");
    JsonNode weather = json.readTree(page(member, registered).body()).get("weather");
    assertThat(weather.get("forecast").get(0).get("date").asString()).isEqualTo(day);
    assertThat(weather.get("warnings").get(0).get("level").asString()).isEqualTo("ORANGE");
  }

  @Test
  void aSnapshotJobsDoesNotHaveEndsInTheDeadLetterQueue() {
    UUID missing = UUID.randomUUID();

    announce(missing);

    assertThat(deadLettered(missing)).isTrue();
  }

  /** Whether the message {@code messageId} reaches Core's dead-letter queue within 20 s. */
  private boolean deadLettered(UUID messageId) {
    long deadline = System.currentTimeMillis() + 20_000;
    while (System.currentTimeMillis() < deadline) {
      Message message = rabbit.receive("core.dlq", 1000);
      if (message != null
          && messageId.toString().equals(message.getMessageProperties().getMessageId())) {
        return true;
      }
    }
    return false;
  }

  /** The member registers a public event in Beja, at the same time as this test's event. */
  private HttpResponse<String> register() throws IOException, InterruptedException {
    String body =
        """
        {"name": "Feira de Beja", "description": "Teste.", "type": "PUBLIC",
         "accessCode": "BEJA-%s", "startDateTime": "%s", "endDateTime": "%s",
         "venue": {"name": "Castelo", "address": "Beja", "district": "Beja",
                   "latitude": 38.015, "longitude": -7.863},
         "capacity": 500}
        """
            .formatted(UUID.randomUUID().toString().substring(0, 6).toUpperCase(), START, END);
    return HTTP.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/events"))
            .header("Authorization", "Bearer " + token(member))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private static String forecast(String day) {
    return "{\"updatedAt\": \"2026-11-27T09:31:02Z\", \"days\": [{\"date\": \""
        + day
        + "\", \"conditions\": \"Céu limpo\", \"minTemperature\": 8.3,"
        + " \"maxTemperature\": 16.6, \"precipitationProbability\": 0}]}";
  }

  /** Stores a snapshot as Jobs does, fetched now, and returns its id. */
  private UUID snapshot(String kind, String content) {
    UUID id = UUID.randomUUID();
    jobs.update(
        "INSERT INTO weather_snapshot (id, district, kind, content, fetched_at, published_at)"
            + " VALUES (?, ?, CAST(? AS weather_snapshot_kind), CAST(? AS jsonb), clock_timestamp(),"
            + " clock_timestamp())",
        id,
        DISTRICT,
        kind,
        content);
    return id;
  }

  private static String warnings(String... warnings) {
    return "{\"warnings\": [" + String.join(", ", warnings) + "]}";
  }

  private static String warning(String type, String level, Instant start, Instant end) {
    return "{\"awarenessType\": \""
        + type
        + "\", \"level\": \""
        + level
        + "\", \"start\": \""
        + start
        + "\", \"end\": \""
        + end
        + "\", \"description\": null}";
  }

  /** Publishes Jobs' WeatherUpdated for the snapshot. */
  private void announce(UUID snapshot) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setMessageId(snapshot.toString());
    String body = "{\"district\": \"" + DISTRICT + "\", \"snapshotId\": \"" + snapshot + "\"}";
    rabbit.send(
        "crowded.events",
        "weather.updated",
        MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
            .andProperties(properties)
            .build());
  }

  private void waitFor(String countQuery, int expected) throws InterruptedException {
    long deadline = System.currentTimeMillis() + 15_000;
    while (jdbc.queryForObject(countQuery, Integer.class, event) != expected
        && System.currentTimeMillis() < deadline) {
      Thread.sleep(100);
    }
    assertThat(jdbc.queryForObject(countQuery, Integer.class, event)).isEqualTo(expected);
  }

  /** The next NotificationRequested about {@code cause}, or null; others are skipped. */
  private Message receiveFor(UUID cause, long timeoutMillis) {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    for (long left = timeoutMillis; left > 0; left = deadline - System.currentTimeMillis()) {
      Message message = rabbit.receive(queue, left);
      if (message != null
          && json.readTree(message.getBody()).get("causeId").asString().equals(cause.toString())) {
        return message;
      }
    }
    return null;
  }

  private HttpResponse<String> page(UUID user) throws IOException, InterruptedException {
    return page(user, event);
  }

  private HttpResponse<String> page(UUID user, UUID eventId)
      throws IOException, InterruptedException {
    return HTTP.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/events/" + eventId))
            .header("Authorization", "Bearer " + token(user))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
