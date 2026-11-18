package com.crowded.core.ticket;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
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
 * Rows and requests shared by the ticket integration tests: one organization with its events and
 * participants, created with unique ids, and the calls to Core's ticket endpoints.
 */
final class TicketTestData {

  private static final String PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

  /** An answer of Core, with its body read as JSON (null when empty). */
  record Response(HttpStatusCode status, JsonNode body) {}

  private final JdbcTemplate jdbc;
  private final JsonMapper json;
  private final RestClient client;
  private final Instant start;
  private final UUID organization = UUID.randomUUID();

  /** Creates the organization whose events the tests insert, starting at {@code start}. */
  TicketTestData(JdbcTemplate jdbc, JsonMapper json, int port, Instant start) {
    this.jdbc = jdbc;
    this.json = json;
    this.client = RestClient.create("http://localhost:" + port);
    this.start = start;
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now())",
        organization,
        "Festivais " + organization);
  }

  RestClient client() {
    return client;
  }

  Response exchange(RestClient.RequestHeadersSpec<?> request) {
    return request.exchange(
        (req, res) -> {
          byte[] body = res.getBody().readAllBytes();
          return new Response(res.getStatusCode(), body.length == 0 ? null : json.readTree(body));
        });
  }

  /** Imports {@code file} as a ticket PDF with the participant's {@code token}. */
  Response upload(String token, byte[] file) {
    MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();
    parts.add(
        "file",
        new ByteArrayResource(file) {
          @Override
          public String getFilename() {
            return "bilhete.pdf";
          }
        });
    return exchange(
        client
            .post()
            .uri("/tickets")
            .contentType(MediaType.MULTIPART_FORM_DATA)
            .header("Authorization", "Bearer " + token)
            .body(parts));
  }

  /** A ticketed event with {@code code}; an {@code ENDED} one started 60 days earlier. */
  UUID insertEvent(String code, String status) {
    return insertEvent("TICKETED", code, status);
  }

  UUID insertPublicEvent(String code) {
    return insertEvent("PUBLIC", code, "SCHEDULED");
  }

  private UUID insertEvent(String type, String code, String status) {
    UUID id = UUID.randomUUID();
    Instant begins = "ENDED".equals(status) ? start.minus(60, ChronoUnit.DAYS) : start;
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
        Timestamp.from(begins),
        Timestamp.from(begins.minus(1, ChronoUnit.HOURS)),
        Timestamp.from(begins.plus(3, ChronoUnit.HOURS)),
        status);
    return id;
  }

  void insertParticipant(UUID id) {
    jdbc.update(
        "INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at,"
            + " active, name) VALUES (?, NULL, ?, ?, 'PARTICIPANT'::role, now(), true, 'Pessoa')",
        id,
        id + "@crowded.test",
        PASSWORD_HASH);
  }
}
