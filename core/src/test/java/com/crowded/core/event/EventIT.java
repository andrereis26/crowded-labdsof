package com.crowded.core.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A member registers, lists and corrects their organization's events through the API: Core stores
 * the event with its queue points and confirmation settings, keeps each code to one upcoming event
 * and keeps the participants already connected when the code changes.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class EventIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final Instant START =
      Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  private static final String PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  private final UUID organization = UUID.randomUUID();
  private final UUID otherOrganization = UUID.randomUUID();
  private final UUID member = UUID.randomUUID();
  private final UUID outsider = UUID.randomUUID();
  private final UUID deactivated = UUID.randomUUID();
  private final UUID participant = UUID.randomUUID();
  private final String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();

  @BeforeEach
  void createTwoOrganizationsAndTheirMembers() {
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now()), (?, ?, now())",
        organization,
        "Margens " + organization,
        otherOrganization,
        "Outra " + otherOrganization);
    insertUser(member, organization, "ORGANIZATION_MEMBER", true);
    insertUser(outsider, otherOrganization, "ORGANIZATION_MEMBER", true);
    insertUser(deactivated, organization, "ORGANIZATION_MEMBER", false);
    insertUser(participant, null, "PARTICIPANT", true);
  }

  @Test
  void registersAPublicEventWithItsSetupAndListsIt() throws Exception {
    HttpResponse<String> response = send(member, "POST", "/events", publicEvent("sj-" + suffix));

    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode created = json.readTree(response.body());
    UUID event = UUID.fromString(created.get("id").asString());
    assertThat(response.headers().firstValue("Location")).hasValue("/events/" + event);
    assertThat(created.get("status").asString()).isEqualTo("SCHEDULED");
    assertThat(created.get("accessCode").asString()).isEqualTo("SJ-" + suffix);
    assertThat(
            jdbc.queryForObject(
                "SELECT organization_id FROM event WHERE id = ?", UUID.class, event))
        .isEqualTo(organization);
    assertThat(
            jdbc.queryForList(
                "SELECT name FROM queue_point WHERE event_id = ? ORDER BY position",
                String.class,
                event))
        .containsExactly("Porta A", "Bar principal");
    // Only the categories whose choice differs from their default get a row.
    assertThat(
            jdbc.queryForList(
                "SELECT report_category_code || '=' || prompts_enabled"
                    + " FROM event_category_setting WHERE event_id = ?"
                    + " ORDER BY report_category_code",
                String.class,
                event))
        .containsExactly(
            "ACCESSIBILITY_BARRIER=false", "ON_SITE_PROBLEM=false", "QUEUE_LENGTH=false");
    assertThat(
            jdbc.queryForObject(
                "SELECT venue_radius_meters FROM event WHERE id = ?", Integer.class, event))
        .isEqualTo(1000);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM audit_log_entry WHERE entity_id = ? AND action = 'EVENT_CREATED'"
                    + " AND actor_id = ?",
                Integer.class,
                event,
                member))
        .isOne();

    JsonNode listed = json.readTree(send(member, "GET", "/organizations/me/events", null).body());
    assertThat(listed).hasSize(1);
    assertThat(listed.get(0).get("id").asString()).isEqualTo(event.toString());
    assertThat(listed.get(0).get("confirmations").get("categories").get(0).asString())
        .isEqualTo("CROWD_LEVEL");
    assertThat(json.readTree(send(outsider, "GET", "/organizations/me/events", null).body()))
        .isEmpty();
  }

  @Test
  void refusesACodeAnotherUpcomingEventUsesButNotOneOfAnEndedEvent() throws Exception {
    insertEvent(otherOrganization, "PUBLIC", "TAKEN-" + suffix, "SCHEDULED");
    insertEvent(otherOrganization, "TICKETED", "OLD-" + suffix, "ENDED");

    HttpResponse<String> taken = send(member, "POST", "/events", publicEvent("taken-" + suffix));
    HttpResponse<String> reused = send(member, "POST", "/events", publicEvent("OLD-" + suffix));

    assertThat(taken.statusCode()).isEqualTo(409);
    assertThat(json.readTree(taken.body()).get("code").asString()).isEqualTo("EVENT_CODE_TAKEN");
    assertThat(reused.statusCode()).isEqualTo(201);
  }

  @Test
  void correctsTheCodeAndKeepsTheParticipantsConnected() throws Exception {
    UUID event = insertEvent(organization, "PUBLIC", "FIRST-" + suffix, "SCHEDULED");
    UUID connection = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO event_connection (id, user_id, event_id, connected_at) VALUES (?, ?, ?, now())",
        connection,
        participant,
        event);
    jdbc.update("INSERT INTO access_code_connection (id) VALUES (?)", connection);

    HttpResponse<String> response =
        send(
            member,
            "PATCH",
            "/events/" + event,
            "{\"name\": \"Concerto corrigido\", \"accessCode\": \"second-"
                + suffix
                + "\", \"capacity\": 900, \"queuePoints\": [\"Entrada\"]}");

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode corrected = json.readTree(response.body());
    assertThat(corrected.get("accessCode").asString()).isEqualTo("SECOND-" + suffix);
    assertThat(corrected.get("connectedUsers").asLong()).isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT name || '/' || access_code || '/' || capacity FROM event WHERE id = ?",
                String.class,
                event))
        .isEqualTo("Concerto corrigido/SECOND-" + suffix + "/900");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection WHERE id = ?", Integer.class, connection))
        .isOne();
  }

  @Test
  void refusesWhoIsNotAnActiveMemberOfTheOrganization() throws Exception {
    UUID event = insertEvent(organization, "TICKETED", "OWN-" + suffix, "SCHEDULED");

    assertThat(send(deactivated, "POST", "/events", publicEvent("D-" + suffix)).statusCode())
        .isEqualTo(403);
    assertThat(send(participant, "GET", "/organizations/me/events", null).statusCode())
        .isEqualTo(403);
    HttpResponse<String> foreign =
        send(outsider, "PATCH", "/events/" + event, "{\"name\": \"Meu\"}");
    assertThat(foreign.statusCode()).isEqualTo(403);
    assertThat(json.readTree(foreign.body()).get("code").asString())
        .isEqualTo("NOT_EVENT_ORGANIZATION");
    assertThat(jdbc.queryForObject("SELECT name FROM event WHERE id = ?", String.class, event))
        .isEqualTo("Concerto");
  }

  @Test
  void refusesAnEventThatStartsInThePastOrInAnUnknownDistrict() throws Exception {
    HttpResponse<String> past =
        send(
            member,
            "POST",
            "/events",
            publicEvent("P-" + suffix)
                .replace(START.toString(), Instant.now().minus(1, ChronoUnit.DAYS).toString()));
    HttpResponse<String> nowhere =
        send(member, "POST", "/events", publicEvent("N-" + suffix).replace("Porto", "Galiza"));

    assertThat(past.statusCode()).isEqualTo(400);
    assertThat(json.readTree(nowhere.body()).get("code").asString()).isEqualTo("DISTRICT_UNKNOWN");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event WHERE organization_id = ?",
                Integer.class,
                organization))
        .isZero();
  }

  private String publicEvent(String code) {
    return """
        {"name": "São João no Parque", "description": "Concerto gratuito.", "type": "PUBLIC",
         "accessCode": "%s", "startDateTime": "%s", "endDateTime": "%s",
         "venue": {"name": "Parque da Cidade", "address": "Estrada Interior da Circunvalação",
                   "district": "Porto", "latitude": 41.17, "longitude": -8.678},
         "capacity": 20000, "queuePoints": ["Porta A", "Bar principal"],
         "confirmations": {"categories": ["CROWD_LEVEL"], "venueRadiusMeters": 1000,
                           "highConfidenceAnswers": 8, "maxPromptsPerHour": 3}}
        """
        .formatted(code, START, START.plus(5, ChronoUnit.HOURS));
  }

  private UUID insertEvent(UUID owner, String type, String code, String status) {
    UUID id = UUID.randomUUID();
    Instant start = "ENDED".equals(status) ? START.minus(60, ChronoUnit.DAYS) : START;
    jdbc.update(
        "INSERT INTO event (id, organization_id, name, description, type, ticket_event_code,"
            + " access_code, start_date_time, end_date_time, status, venue_name, address,"
            + " district, latitude, longitude)"
            + " VALUES (?, ?, 'Concerto', 'Teste', ?::event_type, ?, ?, ?, ?, ?::event_status,"
            + " 'Coliseu', 'Porto', 'Porto', 41.146, -8.606)",
        id,
        owner,
        type,
        "TICKETED".equals(type) ? code : null,
        "PUBLIC".equals(type) ? code : null,
        Timestamp.from(start),
        Timestamp.from(start.plus(3, ChronoUnit.HOURS)),
        status);
    return id;
  }

  private void insertUser(UUID id, UUID organizationId, String role, boolean active) {
    jdbc.update(
        "INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at,"
            + " active, name) VALUES (?, ?, ?, ?, ?::role, now(), ?, 'Pessoa')",
        id,
        organizationId,
        id + "@crowded.test",
        PASSWORD_HASH,
        role,
        active);
  }

  private HttpResponse<String> send(UUID user, String method, String path, String body)
      throws IOException, InterruptedException {
    HttpRequest.Builder request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
            .header("Authorization", "Bearer " + token(user));
    if (body == null) {
      request.method(method, HttpRequest.BodyPublishers.noBody());
    } else {
      request
          .header("Content-Type", "application/json")
          .method(method, HttpRequest.BodyPublishers.ofString(body));
    }
    return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
  }
}
