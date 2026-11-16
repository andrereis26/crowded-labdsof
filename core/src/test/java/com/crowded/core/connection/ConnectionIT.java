package com.crowded.core.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A participant connects to a public event with its access code through the API: Core finds the
 * event from the code alone, opens its page to them, never connects them twice, answers the same
 * for every code that leads to no event, and keeps them connected when the code changes.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class ConnectionIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final Instant START =
      Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  private static final String PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @Autowired private ConnectionRepository connections;

  private final UUID organization = UUID.randomUUID();
  private final UUID member = UUID.randomUUID();
  private final UUID participant = UUID.randomUUID();
  private final String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

  @BeforeEach
  void createAnOrganizationAMemberAndAParticipant() {
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now())",
        organization,
        "Arraiais " + organization);
    insertUser(member, organization, "ORGANIZATION_MEMBER");
    insertUser(participant, null, "PARTICIPANT");
  }

  @Test
  void connectsWithTheCodeAndOpensTheEventPage() throws Exception {
    UUID event = insertEvent("PUBLIC", "SJ-" + suffix, "SCHEDULED");
    assertThat(send(participant, "GET", "/events/" + event, null).statusCode()).isEqualTo(403);

    HttpResponse<String> response = join(participant, " sj-" + suffix.toLowerCase() + " ");

    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode body = json.readTree(response.body());
    UUID connection = UUID.fromString(body.get("connectionId").asString());
    assertThat(body.get("event").get("id").asString()).isEqualTo(event.toString());
    assertThat(body.get("event").get("name").asString()).isEqualTo("Arraial");
    assertThat(Instant.parse(body.get("event").get("startDateTime").asString())).isEqualTo(START);
    assertThat(body.get("event").get("venueName").asString()).isEqualTo("Fontainhas");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection c JOIN access_code_connection a"
                    + " ON a.id = c.id WHERE c.id = ? AND c.user_id = ? AND c.event_id = ?",
                Integer.class,
                connection,
                participant,
                event))
        .isOne();
    assertThat(send(participant, "GET", "/events/" + event, null).statusCode()).isEqualTo(200);
  }

  @Test
  void keepsOneConnectionWhenTheCodeIsUsedAgainOrSentTwiceAtOnce() throws Exception {
    UUID event = insertEvent("PUBLIC", "TWICE-" + suffix, "SCHEDULED");

    List<CompletableFuture<HttpResponse<String>>> atOnce =
        List.of(joinAsync("TWICE-" + suffix), joinAsync("TWICE-" + suffix));
    List<Integer> statuses = atOnce.stream().map(f -> f.join().statusCode()).toList();
    HttpResponse<String> again = join(participant, "TWICE-" + suffix);

    assertThat(statuses).containsExactlyInAnyOrder(201, 200);
    assertThat(again.statusCode()).isEqualTo(200);
    List<String> ids =
        atOnce.stream()
            .map(f -> json.readTree(f.join().body()).get("connectionId").asString())
            .toList();
    assertThat(json.readTree(again.body()).get("connectionId").asString())
        .isEqualTo(ids.get(0))
        .isEqualTo(ids.get(1));
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection WHERE user_id = ? AND event_id = ?",
                Integer.class,
                participant,
                event))
        .isOne();
  }

  @Test
  void answersTheSameForEveryCodeThatLeadsToNoPublicEvent() throws Exception {
    insertEvent("TICKETED", "TICKET-" + suffix, "SCHEDULED");
    insertEvent("PUBLIC", "ENDED-" + suffix, "ENDED");
    insertEvent("PUBLIC", "CANCEL-" + suffix, "CANCELLED");

    List<HttpResponse<String>> refused =
        List.of(
            join(participant, "NONE-" + suffix),
            join(participant, "TICKET-" + suffix),
            join(participant, "ENDED-" + suffix),
            join(participant, "CANCEL-" + suffix),
            join(participant, "não é um código"));

    for (HttpResponse<String> response : refused) {
      assertThat(response.statusCode()).isEqualTo(404);
      JsonNode problem = json.readTree(response.body());
      assertThat(problem.get("code").asString()).isEqualTo("ACCESS_CODE_NOT_FOUND");
      assertThat(problem.get("detail").asString()).isEqualTo("No event was found with this code.");
      assertThat(problem.has("eventId")).isFalse();
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection WHERE user_id = ?",
                Integer.class,
                participant))
        .isZero();
  }

  @Test
  void keepsTheParticipantConnectedWhenTheOrganizationChangesTheCode() throws Exception {
    UUID event = insertEvent("PUBLIC", "OLD-" + suffix, "SCHEDULED");
    String connection =
        json.readTree(join(participant, "OLD-" + suffix).body()).get("connectionId").asString();

    HttpResponse<String> changed =
        send(member, "PATCH", "/events/" + event, "{\"accessCode\":\"NEW-" + suffix + "\"}");

    assertThat(changed.statusCode()).isEqualTo(200);
    assertThat(connections.isConnected(participant, event)).isTrue();
    assertThat(send(participant, "GET", "/events/" + event, null).statusCode()).isEqualTo(200);
    assertThat(join(participant, "OLD-" + suffix).statusCode()).isEqualTo(404);
    HttpResponse<String> withNewCode = join(participant, "NEW-" + suffix);
    assertThat(withNewCode.statusCode()).isEqualTo(200);
    assertThat(json.readTree(withNewCode.body()).get("connectionId").asString())
        .isEqualTo(connection);
  }

  @Test
  void connectsOnlyParticipants() throws Exception {
    insertEvent("PUBLIC", "STAFF-" + suffix, "SCHEDULED");

    HttpResponse<String> response = join(member, "STAFF-" + suffix);

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("PARTICIPANT_ONLY");
  }

  @Test
  void refusesARequestWithoutAToken() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/connections"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"accessCode\":\"SAOJOAO26\"}"))
            .build();

    assertThat(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(401);
  }

  private UUID insertEvent(String type, String code, String status) {
    UUID id = UUID.randomUUID();
    Instant start = "ENDED".equals(status) ? START.minus(60, ChronoUnit.DAYS) : START;
    jdbc.update(
        "INSERT INTO event (id, organization_id, name, description, type, ticket_event_code,"
            + " access_code, start_date_time, end_date_time, status, venue_name, address,"
            + " district, latitude, longitude)"
            + " VALUES (?, ?, 'Arraial', 'Teste', ?::event_type, ?, ?, ?, ?, ?::event_status,"
            + " 'Fontainhas', 'Porto', 'Porto', 41.14, -8.6)",
        id,
        organization,
        type,
        "TICKETED".equals(type) ? code : null,
        "PUBLIC".equals(type) ? code : null,
        Timestamp.from(start),
        Timestamp.from(start.plus(3, ChronoUnit.HOURS)),
        status);
    return id;
  }

  private void insertUser(UUID id, UUID organizationId, String role) {
    jdbc.update(
        "INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at,"
            + " active, name) VALUES (?, ?, ?, ?, ?::role, now(), true, 'Pessoa')",
        id,
        organizationId,
        id + "@crowded.test",
        PASSWORD_HASH,
        role);
  }

  private HttpResponse<String> join(UUID user, String code)
      throws IOException, InterruptedException {
    return send(user, "POST", "/connections", body(code));
  }

  private CompletableFuture<HttpResponse<String>> joinAsync(String code) {
    return HTTP.sendAsync(request(participant, "POST", "/connections", body(code)), ofString());
  }

  private String body(String code) {
    return json.writeValueAsString(java.util.Map.of("accessCode", code));
  }

  private HttpResponse<String> send(UUID user, String method, String path, String body)
      throws IOException, InterruptedException {
    return HTTP.send(request(user, method, path, body), ofString());
  }

  private HttpRequest request(UUID user, String method, String path, String body) {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + token(user));
    if (body == null) {
      return request.method(method, HttpRequest.BodyPublishers.noBody()).build();
    }
    return request
        .header("Content-Type", "application/json")
        .method(method, HttpRequest.BodyPublishers.ofString(body))
        .build();
  }

  private static HttpResponse.BodyHandler<String> ofString() {
    return HttpResponse.BodyHandlers.ofString();
  }
}
