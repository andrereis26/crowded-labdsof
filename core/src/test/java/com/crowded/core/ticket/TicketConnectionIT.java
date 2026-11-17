package com.crowded.core.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A participant imports a ticket whose event code matches a registered event: Core connects them
 * from the code alone, keeps the ticket with its extraction and the entry code encrypted, links one
 * ticket to one account, and shows the registered event's data.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class TicketConnectionIT extends IntegrationTest {

  private static final Instant START =
      Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  private static final String PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @Autowired private ConnectionRepository connections;

  private final UUID organization = UUID.randomUUID();
  private final UUID ana = UUID.randomUUID();
  private final UUID bruno = UUID.randomUUID();
  private final String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  private final String entryCode = "QR-" + UUID.randomUUID();

  private RestClient client;

  @BeforeEach
  void createAnOrganizationAndTwoParticipants() {
    client = RestClient.create("http://localhost:" + port);
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now())",
        organization,
        "Festivais " + organization);
    insertUser(ana);
    insertUser(bruno);
  }

  private record Response(HttpStatusCode status, JsonNode body) {}

  private Response upload(UUID user, byte[] file) {
    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add(
        "file",
        new ByteArrayResource(file) {
          @Override
          public String getFilename() {
            return "bilhete.pdf";
          }
        });
    return client
        .post()
        .uri("/tickets")
        .contentType(MediaType.MULTIPART_FORM_DATA)
        .header("Authorization", "Bearer " + token(user))
        .body(parts)
        .exchange(
            (req, res) -> {
              byte[] body = res.getBody().readAllBytes();
              return new Response(
                  res.getStatusCode(), body.length == 0 ? null : json.readTree(body));
            });
  }

  @Test
  void connectsToTheEventOfTheCodeAndStoresTheTicket() {
    UUID event = insertEvent("FEST-" + suffix, "SCHEDULED");

    Response response = upload(ana, TicketPdfs.ticketOfEvent("FEST-" + suffix, entryCode));

    assertThat(response.status().value()).isEqualTo(200);
    JsonNode body = response.body();
    assertThat(body.get("status").asString()).isEqualTo("CONNECTED");
    UUID ticket = UUID.fromString(body.get("ticketId").asString());
    assertThat(body.get("connectionId").asString()).isEqualTo(ticket.toString());
    assertThat(body.has("importId") && !body.get("importId").isNull()).isFalse();
    JsonNode shown = body.get("event");
    assertThat(shown.get("id").asString()).isEqualTo(event.toString());
    assertThat(shown.get("name").asString()).isEqualTo("Festival Teste");
    assertThat(Instant.parse(shown.get("startDateTime").asString())).isEqualTo(START);
    assertThat(shown.get("venueName").asString()).isEqualTo("Cais de Teste");
    assertThat(body.get("ticket").get("gate").asString()).isEqualTo("A");
    assertThat(connections.isConnected(ana, event)).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection c JOIN ticket t ON t.id = c.id"
                    + " WHERE c.id = ? AND c.user_id = ? AND c.event_id = ?"
                    + " AND t.match_method = 'EXTRACTED_CODE' AND t.import_method = 'PDF_UPLOAD'",
                Integer.class,
                ticket,
                ana,
                event))
        .isOne();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket_extraction"
                    + " WHERE ticket_id = ? AND extracted_event_code = ? AND ai_generated",
                Integer.class,
                ticket,
                "FEST-" + suffix))
        .isOne();
  }

  @Test
  void storesTheEntryCodeEncryptedAndItsHashKeyed() {
    insertEvent("SEC-" + suffix, "SCHEDULED");

    UUID ticket =
        UUID.fromString(
            upload(ana, TicketPdfs.ticketOfEvent("SEC-" + suffix, entryCode))
                .body()
                .get("ticketId")
                .asString());

    byte[] storedCode =
        jdbc.queryForObject("SELECT entry_code FROM ticket WHERE id = ?", byte[].class, ticket);
    String storedHash =
        jdbc.queryForObject(
            "SELECT entry_code_hash FROM ticket WHERE id = ?", String.class, ticket);
    assertThat(new String(storedCode, java.nio.charset.StandardCharsets.UTF_8))
        .doesNotContain(entryCode);
    assertThat(storedHash).matches("[0-9a-f]{64}").doesNotContain(entryCode);
  }

  @Test
  void givesTheSameConnectionWhenTheOwnerImportsTheTicketAgain() {
    UUID event = insertEvent("AGAIN-" + suffix, "SCHEDULED");
    byte[] file = TicketPdfs.ticketOfEvent("AGAIN-" + suffix, entryCode);

    String first = upload(ana, file).body().get("ticketId").asString();
    Response again = upload(ana, file);

    assertThat(again.status().value()).isEqualTo(200);
    assertThat(again.body().get("status").asString()).isEqualTo("CONNECTED");
    assertThat(again.body().get("ticketId").asString()).isEqualTo(first);
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection WHERE user_id = ? AND event_id = ?",
                Integer.class,
                ana,
                event))
        .isOne();
  }

  @Test
  void refusesTheSameTicketFromAnotherAccount() {
    UUID event = insertEvent("USED-" + suffix, "SCHEDULED");
    byte[] file = TicketPdfs.ticketOfEvent("USED-" + suffix, entryCode);
    upload(ana, file);

    Response refused = upload(bruno, file);

    assertThat(refused.status().value()).isEqualTo(409);
    assertThat(refused.body().get("code").asString()).isEqualTo("TICKET_ALREADY_IMPORTED");
    assertThat(connections.isConnected(bruno, event)).isFalse();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection WHERE event_id = ?", Integer.class, event))
        .isOne();
  }

  @Test
  void connectsAnotherTicketOfTheSameEventToAnotherAccount() {
    UUID event = insertEvent("TWO-" + suffix, "SCHEDULED");
    upload(ana, TicketPdfs.ticketOfEvent("TWO-" + suffix, entryCode));

    Response other = upload(bruno, TicketPdfs.ticketOfEvent("TWO-" + suffix, entryCode + "-B"));

    assertThat(other.body().get("status").asString()).isEqualTo("CONNECTED");
    assertThat(connections.isConnected(bruno, event)).isTrue();
  }

  @Test
  void opensTheEventPageToTheTicketHolderOnly() {
    UUID event = insertEvent("PAGE-" + suffix, "SCHEDULED");
    assertThat(page(ana, event)).isEqualTo(403);

    upload(ana, TicketPdfs.ticketOfEvent("PAGE-" + suffix, entryCode));

    assertThat(page(ana, event)).isEqualTo(200);
    assertThat(page(bruno, event)).isEqualTo(403);
  }

  @Test
  void asksForTheCodeWhenNoRegisteredEventHasIt() {
    Response response = upload(ana, TicketPdfs.ticketOfEvent("NONE-" + suffix, entryCode));

    assertThat(response.body().get("status").asString()).isEqualTo("NEEDS_EVENT_CODE");
    assertThat(response.body().get("importId").asString()).isNotBlank();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection WHERE user_id = ?", Integer.class, ana))
        .isZero();
  }

  @Test
  void matchesOnlyTicketedEventsThatHaveNotEndedOrBeenCancelled() {
    insertEvent("ENDED-" + suffix, "ENDED");
    insertEvent("CANCEL-" + suffix, "CANCELLED");
    insertPublicEvent("PUB-" + suffix);

    for (String code : new String[] {"ENDED-" + suffix, "CANCEL-" + suffix, "PUB-" + suffix}) {
      Response response = upload(ana, TicketPdfs.ticketOfEvent(code, entryCode + code));

      assertThat(response.body().get("status").asString()).isEqualTo("NEEDS_EVENT_CODE");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection WHERE user_id = ?", Integer.class, ana))
        .isZero();
  }

  private int page(UUID user, UUID event) {
    return client
        .get()
        .uri("/events/" + event)
        .header("Authorization", "Bearer " + token(user))
        .exchange((req, res) -> res.getStatusCode().value());
  }

  private UUID insertEvent(String code, String status) {
    return insertEvent("TICKETED", code, status);
  }

  private UUID insertPublicEvent(String code) {
    return insertEvent("PUBLIC", code, "SCHEDULED");
  }

  private UUID insertEvent(String type, String code, String status) {
    UUID id = UUID.randomUUID();
    Instant start = "ENDED".equals(status) ? START.minus(60, ChronoUnit.DAYS) : START;
    jdbc.update(
        "INSERT INTO event (id, organization_id, name, description, type, ticket_event_code,"
            + " access_code, start_date_time, doors_open_at, end_date_time, status, venue_name,"
            + " address, district, latitude, longitude)"
            + " VALUES (?, ?, 'Festival Teste', 'Teste', ?::event_type, ?, ?, ?, ?, ?,"
            + " ?::event_status, 'Cais de Teste', 'Porto', 'Porto', 41.14, -8.6)",
        id,
        organization,
        type,
        "TICKETED".equals(type) ? code : null,
        "PUBLIC".equals(type) ? code : null,
        Timestamp.from(start),
        Timestamp.from(start.minus(1, ChronoUnit.HOURS)),
        Timestamp.from(start.plus(3, ChronoUnit.HOURS)),
        status);
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
