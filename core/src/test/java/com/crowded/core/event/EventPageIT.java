package com.crowded.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import com.crowded.core.event.application.service.EventPages;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** The event page, its history and its live stream, against the database, Redis and the API. */
class EventPageIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final Instant START =
      Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  private static final String PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @Autowired private CacheManager caches;

  private final UUID organization = UUID.randomUUID();
  private final UUID member = UUID.randomUUID();
  private final UUID participant = UUID.randomUUID();
  private final UUID stranger = UUID.randomUUID();
  private final UUID event = UUID.randomUUID();

  @BeforeEach
  void createAnEventWithAConnectedParticipant() {
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now())",
        organization,
        "Ribeira Página " + organization);
    jdbc.update(
        "INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at, active, name)"
            + " VALUES (?, ?, ?, ?, 'ORGANIZATION_MEMBER', now(), true, 'Membro'),"
            + " (?, NULL, ?, ?, 'PARTICIPANT', now(), true, 'Ana'),"
            + " (?, NULL, ?, ?, 'PARTICIPANT', now(), true, 'Bruno')",
        member,
        organization,
        member + "@crowded.test",
        PASSWORD_HASH,
        participant,
        participant + "@crowded.test",
        PASSWORD_HASH,
        stranger,
        stranger + "@crowded.test",
        PASSWORD_HASH);
    jdbc.update(
        "INSERT INTO event (id, organization_id, name, description, type, ticket_event_code,"
            + " start_date_time, doors_open_at, end_date_time, status, venue_name, address,"
            + " district, latitude, longitude)"
            + " VALUES (?, ?, 'Concerto', 'Teste', 'TICKETED', ?, ?, ?, ?, 'SCHEDULED',"
            + " 'Coliseu', 'Porto', 'Porto', 41.146, -8.606)",
        event,
        organization,
        "PG-" + event,
        Timestamp.from(START),
        Timestamp.from(START.minus(90, ChronoUnit.MINUTES)),
        Timestamp.from(START.plus(3, ChronoUnit.HOURS)));
    jdbc.update(
        "INSERT INTO event_connection (id, user_id, event_id, connected_at) VALUES (?, ?, ?, now())",
        UUID.randomUUID(),
        participant,
        event);
  }

  @Test
  void showsAnEventWithoutChangesAndAnEmptyHistory() throws Exception {
    HttpResponse<String> page = get(participant, "");
    HttpResponse<String> history = get(participant, "/changes");

    assertThat(page.statusCode()).isEqualTo(200);
    JsonNode body = json.readTree(page.body());
    assertThat(body.get("status").asString()).isEqualTo("SCHEDULED");
    assertThat(body.get("startDateTime").asString()).isEqualTo(START.toString());
    assertThat(body.get("venue").get("name").asString()).isEqualTo("Coliseu");
    assertThat(body.get("organization").get("name").asString()).startsWith("Ribeira Página");
    assertThat(body.get("latestChange").isNull()).isTrue();
    assertThat(history.statusCode()).isEqualTo(200);
    assertThat(json.readTree(history.body()).size()).isZero();
  }

  @Test
  void dropsTheCachedPageWhenAChangeIsPublishedAndListsTheChangesNewestFirst() throws Exception {
    assertThat(get(member, "").statusCode()).isEqualTo(200);
    assertThat(caches.getCache(EventPages.CACHE).get(event)).isNotNull();

    publish(
        "{\"type\": \"TIME_CHANGE\", \"newValue\": {\"doorsOpenAt\": \""
            + START.minus(60, ChronoUnit.MINUTES)
            + "\"}}");
    publish(
        "{\"type\": \"CANCELLATION\", \"ticketStillValid\": false, \"refundInfo\": \"No site\"}");

    String fresh = get(participant, "").body();
    assertThat(caches.getCache(EventPages.CACHE).get(event)).isNotNull();
    assertThat(json.readTree(get(member, "").body()))
        .as("the same page, read back from the cache")
        .isEqualTo(json.readTree(fresh));
    JsonNode page = json.readTree(fresh);
    assertThat(page.get("status").asString()).isEqualTo("CANCELLED");
    assertThat(page.get("latestChange").get("type").asString()).isEqualTo("CANCELLATION");
    JsonNode history = json.readTree(get(participant, "/changes").body());
    assertThat(history.size()).isEqualTo(2);
    assertThat(history.get(0).get("type").asString()).isEqualTo("CANCELLATION");
    assertThat(history.get(0).get("refundInfo").asString()).isEqualTo("No site");
    assertThat(history.get(0).get("provenance").get("sourceType").asString())
        .isEqualTo("ORGANIZATION");
    assertThat(history.get(1).get("type").asString()).isEqualTo("TIME_CHANGE");
    assertThat(history.get(1).get("newValue").get("doorsOpenAt").asString())
        .isEqualTo(START.minus(60, ChronoUnit.MINUTES).toString());
  }

  @Test
  void refusesAParticipantWhoIsNotConnectedAndAnUnknownEvent() throws Exception {
    for (String path : new String[] {"", "/changes", "/live"}) {
      HttpResponse<String> refused = get(stranger, path);
      assertThat(refused.statusCode()).isEqualTo(403);
      assertThat(json.readTree(refused.body()).get("code").asString()).isEqualTo("NOT_CONNECTED");
    }
    HttpResponse<String> missing =
        send(participant, "http://localhost:" + port + "/events/" + UUID.randomUUID());
    assertThat(missing.statusCode()).isEqualTo(404);
  }

  @Test
  void sendsTheNewPageOnTheLiveStreamWhenAChangeIsPublished() throws Exception {
    HttpResponse<InputStream> stream =
        HTTP.send(
            request(participant, base() + "/live").build(),
            HttpResponse.BodyHandlers.ofInputStream());
    assertThat(stream.statusCode()).isEqualTo(200);
    assertThat(stream.headers().firstValue("Content-Type"))
        .hasValueSatisfying(type -> assertThat(type).startsWith("text/event-stream"));
    BlockingQueue<String> pages = new LinkedBlockingQueue<>();
    Thread reader = Thread.ofVirtual().start(() -> readData(stream.body(), pages));
    try {
      String first = pages.poll(10, TimeUnit.SECONDS);
      assertThat(json.readTree(first).get("status").asString()).isEqualTo("SCHEDULED");

      publish("{\"type\": \"CANCELLATION\", \"ticketStillValid\": false}");

      String next = pages.poll(10, TimeUnit.SECONDS);
      assertThat(next).isNotNull();
      JsonNode updated = json.readTree(next);
      assertThat(updated.get("status").asString()).isEqualTo("CANCELLED");
      assertThat(updated.get("latestChange").get("type").asString()).isEqualTo("CANCELLATION");
    } finally {
      stream.body().close();
      reader.interrupt();
    }
  }

  @Test
  void sendsThePublishedCrowdLevelOnTheLiveStreamAndRefusesAnotherOrganization() throws Exception {
    HttpResponse<InputStream> stream =
        HTTP.send(
            request(participant, base() + "/live").build(),
            HttpResponse.BodyHandlers.ofInputStream());
    BlockingQueue<String> pages = new LinkedBlockingQueue<>();
    Thread reader = Thread.ofVirtual().start(() -> readData(stream.body(), pages));
    try {
      String first = pages.poll(10, TimeUnit.SECONDS);
      assertThat(json.readTree(first).get("crowd").isNull()).isTrue();

      HttpResponse<String> published = putCrowd(member, "{\"level\": \"HIGH\"}");

      assertThat(published.statusCode()).as(published.body()).isEqualTo(200);
      assertThat(json.readTree(published.body()).get("level").asString()).isEqualTo("HIGH");
      String next = pages.poll(10, TimeUnit.SECONDS);
      assertThat(next).isNotNull();
      JsonNode crowd = json.readTree(next).get("crowd");
      assertThat(crowd.get("level").asString()).isEqualTo("HIGH");
      assertThat(crowd.get("provenance").get("sourceType").asString()).isEqualTo("ORGANIZATION");
      assertThat(crowd.get("provenance").get("sourceName").asString())
          .isEqualTo("Ribeira Página " + organization);
      assertThat(Instant.parse(crowd.get("provenance").get("lastUpdatedAt").asString()))
          .isBetween(Instant.now().minusSeconds(30), Instant.now());
    } finally {
      stream.body().close();
      reader.interrupt();
    }

    UUID otherOrganization = UUID.randomUUID();
    UUID outsider = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now())",
        otherOrganization,
        "Outra " + otherOrganization);
    jdbc.update(
        "INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at, active, name)"
            + " VALUES (?, ?, ?, ?, 'ORGANIZATION_MEMBER', now(), true, 'Outro')",
        outsider,
        otherOrganization,
        outsider + "@crowded.test",
        PASSWORD_HASH);
    HttpResponse<String> foreign = putCrowd(outsider, "{\"level\": \"LOW\"}");
    assertThat(foreign.statusCode()).isEqualTo(403);
    assertThat(json.readTree(foreign.body()).get("code").asString())
        .isEqualTo("NOT_EVENT_ORGANIZATION");
    assertThat(putCrowd(participant, "{\"level\": \"LOW\"}").statusCode()).isEqualTo(403);
    assertThat(
            jdbc.queryForObject(
                "SELECT crowd_level::text FROM event WHERE id = ?", String.class, event))
        .isEqualTo("HIGH");
    assertThat(json.readTree(get(participant, "").body()).get("crowd").get("level").asString())
        .isEqualTo("HIGH");
  }

  @Test
  void sendsThePublishedQueueTimesOnTheLiveStreamAndRefusesInvalidOnes() throws Exception {
    UUID bar = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO queue_point (id, event_id, name, position) VALUES (?, ?, 'Porta A', 0),"
            + " (?, ?, 'Bar', 1)",
        UUID.randomUUID(),
        event,
        bar,
        event);
    HttpResponse<InputStream> stream =
        HTTP.send(
            request(participant, base() + "/live").build(),
            HttpResponse.BodyHandlers.ofInputStream());
    BlockingQueue<String> pages = new LinkedBlockingQueue<>();
    Thread reader = Thread.ofVirtual().start(() -> readData(stream.body(), pages));
    try {
      String first = pages.poll(10, TimeUnit.SECONDS);
      assertThat(json.readTree(first).get("queues").size()).isZero();

      HttpResponse<String> published =
          putQueues(member, "{\"queues\": [{\"pointName\": \"Bar\", \"waitMinutes\": 25}]}");

      assertThat(published.statusCode()).as(published.body()).isEqualTo(200);
      assertThat(json.readTree(published.body()).get("queues").get(0).get("waitMinutes").asInt())
          .isEqualTo(25);
      String next = pages.poll(10, TimeUnit.SECONDS);
      assertThat(next).isNotNull();
      JsonNode queues = json.readTree(next).get("queues");
      assertThat(queues.size()).isOne();
      assertThat(queues.get(0).get("pointName").asString()).isEqualTo("Bar");
      assertThat(queues.get(0).get("waitMinutes").asInt()).isEqualTo(25);
      assertThat(queues.get(0).get("provenance").get("sourceName").asString())
          .isEqualTo("Ribeira Página " + organization);
      assertThat(Instant.parse(queues.get(0).get("provenance").get("lastUpdatedAt").asString()))
          .isBetween(Instant.now().minusSeconds(30), Instant.now());
    } finally {
      stream.body().close();
      reader.interrupt();
    }

    assertThat(
            putQueues(member, "{\"queues\": [{\"pointName\": \"Bar\", \"waitMinutes\": -1}]}")
                .statusCode())
        .isEqualTo(400);
    HttpResponse<String> unknown =
        putQueues(member, "{\"queues\": [{\"pointName\": \"WC\", \"waitMinutes\": 5}]}");
    assertThat(unknown.statusCode()).isEqualTo(400);
    assertThat(json.readTree(unknown.body()).get("code").asString())
        .isEqualTo("QUEUE_POINT_UNKNOWN");
    HttpResponse<String> foreign =
        putQueues(participant, "{\"queues\": [{\"pointName\": \"Bar\", \"waitMinutes\": 1}]}");
    assertThat(foreign.statusCode()).isEqualTo(403);
    assertThat(json.readTree(foreign.body()).get("code").asString())
        .isEqualTo("NOT_EVENT_ORGANIZATION");
    assertThat(
            jdbc.queryForObject(
                "SELECT wait_minutes FROM queue_time WHERE queue_point_id = ?", Integer.class, bar))
        .isEqualTo(25);
  }

  /** Puts the data of each server-sent event on {@code pages} until the stream ends. */
  private static void readData(InputStream body, BlockingQueue<String> pages) {
    try (BufferedReader lines =
        new BufferedReader(new InputStreamReader(body, StandardCharsets.UTF_8))) {
      String line;
      while ((line = lines.readLine()) != null) {
        if (line.startsWith("data:")) {
          pages.add(line.substring("data:".length()));
        }
      }
    } catch (IOException e) {
      // The test closed the stream.
    }
  }

  private void publish(String body) throws IOException, InterruptedException {
    HttpResponse<String> response =
        HTTP.send(
            request(member, base() + "/changes")
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).as(response.body()).isEqualTo(201);
  }

  private HttpResponse<String> putCrowd(UUID user, String body)
      throws IOException, InterruptedException {
    return HTTP.send(
        request(user, base() + "/crowd")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> putQueues(UUID user, String body)
      throws IOException, InterruptedException {
    return HTTP.send(
        request(user, base() + "/queues")
            .header("Content-Type", "application/json")
            .PUT(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> get(UUID user, String path)
      throws IOException, InterruptedException {
    return send(user, base() + path);
  }

  private HttpResponse<String> send(UUID user, String url)
      throws IOException, InterruptedException {
    return HTTP.send(request(user, url).build(), HttpResponse.BodyHandlers.ofString());
  }

  private static HttpRequest.Builder request(UUID user, String url) {
    return HttpRequest.newBuilder(URI.create(url))
        .timeout(Duration.ofSeconds(20))
        .header("Authorization", "Bearer " + token(user));
  }

  private String base() {
    return "http://localhost:" + port + "/events/" + event;
  }
}
