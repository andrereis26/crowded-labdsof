package com.crowded.core.connection;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
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
 * A participant lists their events through the API: only their own, those that have not ended
 * first, each with its latest change, its alerts still active and, for a ticket, where they sit.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class MyConnectionsIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final Instant NOW = Instant.now().truncatedTo(ChronoUnit.SECONDS);
  private static final String PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;

  private final UUID organization = UUID.randomUUID();
  private final UUID ana = UUID.randomUUID();
  private final UUID bruno = UUID.randomUUID();
  private final UUID newcomer = UUID.randomUUID();

  @BeforeEach
  void createAnOrganizationAndThreeParticipants() {
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now())",
        organization,
        "Arraiais " + organization);
    insertUser(ana);
    insertUser(bruno);
    insertUser(newcomer);
  }

  @Test
  void listsTheEventsThatHaveNotEndedFirstTheSoonestFirstThenTheOnesThatEnded() throws Exception {
    UUID later = insertEvent("Mais tarde", hours(48), "SCHEDULED");
    UUID ended = insertEvent("Acabou há dois dias", hours(-48), "ENDED");
    UUID running = insertEvent("A decorrer", hours(-1), "ONGOING");
    UUID soon = insertEvent("Em breve", hours(2), "SCHEDULED");
    UUID endedLongAgo = insertEvent("Acabou há uma semana", hours(-170), "ENDED");
    for (UUID event : new UUID[] {later, ended, running, soon, endedLongAgo}) {
      connect(ana, event);
    }

    JsonNode list = list(ana);

    assertThat(names(list))
        .containsExactly(
            "A decorrer", "Em breve", "Mais tarde", "Acabou há dois dias", "Acabou há uma semana");
    assertThat(Instant.parse(list.get("syncedAt").asString())).isAfterOrEqualTo(NOW);
  }

  @Test
  void listsTheFieldsOfTheEventAndHowTheParticipantIsConnected() throws Exception {
    UUID event = insertEvent("Arraial", hours(5), "SCHEDULED");
    UUID byCode = connect(ana, event);

    JsonNode connection = list(ana).get("connections").get(0);

    assertThat(connection.get("connectionId").asString()).isEqualTo(byCode.toString());
    assertThat(connection.get("type").asString()).isEqualTo("ACCESS_CODE");
    JsonNode shown = connection.get("event");
    assertThat(shown.get("id").asString()).isEqualTo(event.toString());
    assertThat(shown.get("name").asString()).isEqualTo("Arraial");
    assertThat(shown.get("status").asString()).isEqualTo("SCHEDULED");
    assertThat(Instant.parse(shown.get("startDateTime").asString())).isEqualTo(hours(5));
    assertThat(Instant.parse(shown.get("doorsOpenAt").asString())).isEqualTo(hours(4));
    assertThat(shown.get("venueName").asString()).isEqualTo("Fontainhas");
    assertThat(shown.get("latestChange").isNull()).isTrue();
    assertThat(shown.get("activeAlerts")).isEmpty();
    assertThat(connection.get("ticket").isNull()).isTrue();
  }

  @Test
  void showsWhereTheTicketPlacesThemWithoutTheEntryCode() throws Exception {
    UUID event = insertEvent("Concerto", hours(5), "SCHEDULED");
    UUID ticket = insertTicketConnection(ana, event, "A", "2", "14");

    JsonNode connection = list(ana).get("connections").get(0);

    assertThat(connection.get("connectionId").asString()).isEqualTo(ticket.toString());
    assertThat(connection.get("type").asString()).isEqualTo("TICKET");
    JsonNode shown = connection.get("ticket");
    assertThat(shown.get("gate").asString()).isEqualTo("A");
    assertThat(shown.get("sector").asString()).isEqualTo("2");
    assertThat(shown.get("seat").asString()).isEqualTo("14");
    assertThat(shown.get("hasEntryCode").asBoolean()).isTrue();
    assertThat(shown.size()).isEqualTo(4);
  }

  @Test
  void showsTheLatestChangeAndOnlyTheAlertsStillActive() throws Exception {
    UUID event = insertEvent("Festival", hours(30), "CANCELLED");
    connect(ana, event);
    insertChange(event, "POSTPONEMENT", "WARNING", "Adiado.", hours(-10));
    UUID latest = insertChange(event, "CANCELLATION", "CRITICAL", "Cancelado.", hours(-2));
    UUID recent = insertAlert(event, "WARNING", "SENT", hours(-1), null, "Entrada Sul fechada.");
    UUID older = insertAlert(event, "INFO", "SENT", hours(-3), null, "Chuva prevista.");
    insertAlert(event, "INFO", "SENT", hours(-4), hours(-2), "Já resolvido.");
    insertAlert(event, "INFO", "DRAFT", null, null, "Ainda é um rascunho.");

    JsonNode shown = list(ana).get("connections").get(0).get("event");

    assertThat(shown.get("status").asString()).isEqualTo("CANCELLED");
    assertThat(shown.get("latestChange").get("id").asString()).isEqualTo(latest.toString());
    assertThat(shown.get("latestChange").get("type").asString()).isEqualTo("CANCELLATION");
    assertThat(shown.get("latestChange").get("severity").asString()).isEqualTo("CRITICAL");
    assertThat(shown.get("latestChange").get("message").asString()).isEqualTo("Cancelado.");
    JsonNode alerts = shown.get("activeAlerts");
    assertThat(alerts).hasSize(2);
    assertThat(alerts.get(0).get("id").asString()).isEqualTo(recent.toString());
    assertThat(alerts.get(0).get("type").asString()).isEqualTo("ENTRANCE_CLOSURE");
    assertThat(alerts.get(0).get("severity").asString()).isEqualTo("WARNING");
    assertThat(alerts.get(0).get("message").asString()).isEqualTo("Entrada Sul fechada.");
    assertThat(Instant.parse(alerts.get(0).get("sentAt").asString())).isEqualTo(hours(-1));
    assertThat(alerts.get(1).get("id").asString()).isEqualTo(older.toString());
  }

  @Test
  void showsEachParticipantOnlyTheirOwnEventsAndAlerts() throws Exception {
    UUID anas = insertEvent("Do Ana", hours(3), "SCHEDULED");
    UUID brunos = insertEvent("Do Bruno", hours(4), "SCHEDULED");
    UUID shared = insertEvent("De ambos", hours(5), "SCHEDULED");
    connect(ana, anas);
    connect(ana, shared);
    connect(bruno, shared);
    insertAlert(brunos, "WARNING", "SENT", hours(-1), null, "Só para quem vai ao do Bruno.");
    insertTicketConnection(bruno, brunos, "B", null, null);

    JsonNode forAna = list(ana);
    JsonNode forBruno = list(bruno);

    assertThat(names(forAna)).containsExactly("Do Ana", "De ambos");
    assertThat(names(forBruno)).containsExactly("Do Bruno", "De ambos");
    assertThat(forAna.toString()).doesNotContain("Só para quem vai").doesNotContain("\"gate\"");
  }

  @Test
  void aParticipantWithoutConnectionsGetsAnEmptyList() throws Exception {
    insertEvent("De mais ninguém", hours(3), "SCHEDULED");

    JsonNode list = list(newcomer);

    assertThat(list.get("connections")).isEmpty();
    assertThat(list.get("syncedAt").isString()).isTrue();
  }

  @Test
  void refusesARequestWithoutAToken() throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me/connections")).build();

    assertThat(HTTP.send(request, HttpResponse.BodyHandlers.ofString()).statusCode())
        .isEqualTo(401);
  }

  private JsonNode list(UUID user) throws Exception {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/me/connections"))
            .header("Authorization", "Bearer " + token(user))
            .build();
    HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
    return json.readTree(response.body());
  }

  private static java.util.List<String> names(JsonNode list) {
    java.util.List<String> names = new java.util.ArrayList<>();
    list.get("connections").forEach(c -> names.add(c.get("event").get("name").asString()));
    return names;
  }

  private static Instant hours(long fromNow) {
    return NOW.plus(fromNow, ChronoUnit.HOURS);
  }

  private UUID insertEvent(String name, Instant start, String status) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO event (id, organization_id, name, description, type, access_code,"
            + " start_date_time, doors_open_at, end_date_time, status, venue_name, address,"
            + " district, latitude, longitude)"
            + " VALUES (?, ?, ?, 'Teste', 'PUBLIC'::event_type, ?, ?, ?, ?, ?::event_status,"
            + " 'Fontainhas', 'Porto', 'Porto', 41.14, -8.6)",
        id,
        organization,
        name,
        "C-" + id.toString().substring(0, 8).toUpperCase(),
        Timestamp.from(start),
        Timestamp.from(start.minus(1, ChronoUnit.HOURS)),
        Timestamp.from(start.plus(3, ChronoUnit.HOURS)),
        status);
    return id;
  }

  private UUID connect(UUID user, UUID event) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO event_connection (id, user_id, event_id, connected_at)"
            + " VALUES (?, ?, ?, now())",
        id,
        user,
        event);
    jdbc.update("INSERT INTO access_code_connection (id) VALUES (?)", id);
    return id;
  }

  private UUID insertTicketConnection(
      UUID user, UUID event, String gate, String sector, String seat) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO event_connection (id, user_id, event_id, connected_at)"
            + " VALUES (?, ?, ?, now())",
        id,
        user,
        event);
    jdbc.update(
        "INSERT INTO ticket (id, gate, sector, seat, entry_code, entry_code_hash, import_method,"
            + " match_method) VALUES (?, ?, ?, ?, ?, ?, 'PDF_UPLOAD'::import_method,"
            + " 'EXTRACTED_CODE'::match_method)",
        id,
        gate,
        sector,
        seat,
        "encrypted".getBytes(java.nio.charset.StandardCharsets.UTF_8),
        "hash-" + id);
    return id;
  }

  private UUID insertChange(UUID event, String type, String severity, String message, Instant at) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO event_change (id, event_id, type, severity, previous_value, new_value,"
            + " message, provenance_source_type, provenance_source_name,"
            + " provenance_last_updated_at, provenance_confirmed)"
            + " VALUES (?, ?, ?::change_type, ?::severity, '{}'::jsonb, '{}'::jsonb, ?,"
            + " 'ORGANIZATION'::source_type, 'Arraiais', ?, true)",
        id,
        event,
        type,
        severity,
        message,
        Timestamp.from(at));
    return id;
  }

  private UUID insertAlert(
      UUID event, String severity, String status, Instant sentAt, Instant resolvedAt, String text) {
    UUID id = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO alert (id, event_id, organization_id, type, severity, message, status,"
            + " sent_at, resolved_at) VALUES (?, ?, ?, 'ENTRANCE_CLOSURE'::alert_type,"
            + " ?::severity, ?, ?::alert_status, ?, ?)",
        id,
        event,
        organization,
        severity,
        text,
        status,
        sentAt == null ? null : Timestamp.from(sentAt),
        resolvedAt == null ? null : Timestamp.from(resolvedAt));
    return id;
  }

  private void insertUser(UUID id) {
    jdbc.update(
        "INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at,"
            + " active, name) VALUES (?, NULL, ?, ?, 'PARTICIPANT'::role, now(), true, 'Pessoa')",
        id,
        id + "@crowded.test",
        PASSWORD_HASH);
  }
}
