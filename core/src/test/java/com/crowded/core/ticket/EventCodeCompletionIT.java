package com.crowded.core.ticket;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.core.IntegrationTest;
import com.crowded.core.connection.domain.repository.ConnectionRepository;
import com.crowded.core.ticket.TicketTestData.Response;
import com.crowded.core.ticket.domain.repository.TicketImportRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A participant whose ticket could not be matched types the event code printed on it: Core decides
 * the event from that code alone, only for the participant's own pending import, and forgets the
 * import and its entry code when it ends.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class EventCodeCompletionIT extends IntegrationTest {

  private static final Instant START =
      Instant.now().plus(20, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private JsonMapper json;
  @Autowired private ConnectionRepository connections;
  @Autowired private TicketImportRepository imports;

  private final UUID ana = UUID.randomUUID();
  private final UUID bruno = UUID.randomUUID();
  private final String suffix = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
  private final String entryCode = "QR-" + UUID.randomUUID();

  private TicketTestData data;

  @BeforeEach
  void createAnOrganizationAndTwoParticipants() {
    data = new TicketTestData(jdbc, json, port, START);
    data.insertParticipant(ana);
    data.insertParticipant(bruno);
  }

  private Response upload(UUID user, byte[] file) {
    return data.upload(token(user), file);
  }

  private Response typeCode(UUID user, Object importId, String eventCode) {
    return data.exchange(
        data.client()
            .post()
            .uri("/ticket-imports/" + importId + "/event-code")
            .contentType(MediaType.APPLICATION_JSON)
            .header("Authorization", "Bearer " + token(user))
            .body(Map.of("eventCode", eventCode)));
  }

  /** Imports a ticket that names an event Crowded does not know, so it waits for the code. */
  private UUID pendingImportOf(UUID user) {
    Response response = upload(user, TicketPdfs.ticketOfEvent("WAIT-" + suffix, entryCode));
    assertThat(response.body().get("status").asString()).isEqualTo("NEEDS_EVENT_CODE");
    return UUID.fromString(response.body().get("importId").asString());
  }

  @Test
  void connectsToTheEventOfTheTypedCodeAndShowsItsSummary() {
    UUID event = data.insertEvent("TYPED-" + suffix, "SCHEDULED");
    UUID pending = pendingImportOf(ana);

    Response response = typeCode(ana, pending, " typed-" + suffix.toLowerCase() + " ");

    assertThat(response.status().value()).isEqualTo(200);
    JsonNode body = response.body();
    assertThat(body.get("status").asString()).isEqualTo("CONNECTED");
    UUID ticket = UUID.fromString(body.get("ticketId").asString());
    assertThat(body.get("connectionId").asString()).isEqualTo(ticket.toString());
    assertThat(body.get("event").get("id").asString()).isEqualTo(event.toString());
    assertThat(body.get("event").get("name").asString()).isEqualTo("Festival Teste");
    assertThat(body.get("ticket").get("gate").asString()).isEqualTo("A");
    assertThat(connections.isConnected(ana, event)).isTrue();
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM ticket WHERE id = ? AND match_method = 'TYPED_CODE'"
                    + " AND import_method = 'PDF_UPLOAD'",
                Integer.class,
                ticket))
        .isOne();
    assertThat(imports.findById(pending)).isEmpty();
  }

  @Test
  void deletesTheImportWhenNoRegisteredEventHasTheTypedCode() {
    UUID pending = pendingImportOf(ana);

    Response response = typeCode(ana, pending, "NOPE-" + suffix);

    assertThat(response.status().value()).isEqualTo(404);
    assertThat(response.body().get("code").asString()).isEqualTo("EVENT_NOT_ON_CROWDED");
    assertThat(imports.findById(pending)).isEmpty();
    assertThat(typeCode(ana, pending, "NOPE-" + suffix).body().get("code").asString())
        .isEqualTo("IMPORT_NOT_FOUND");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection WHERE user_id = ?", Integer.class, ana))
        .isZero();
  }

  @Test
  void treatsACodeThatCannotBeAnEventCodeAsNoEvent() {
    UUID pending = pendingImportOf(ana);

    Response response = typeCode(ana, pending, "not a code!");

    assertThat(response.status().value()).isEqualTo(404);
    assertThat(response.body().get("code").asString()).isEqualTo("EVENT_NOT_ON_CROWDED");
    assertThat(imports.findById(pending)).isEmpty();
  }

  @Test
  void refusesABlankCodeAndKeepsTheImport() {
    UUID pending = pendingImportOf(ana);

    Response response = typeCode(ana, pending, "  ");

    assertThat(response.status().value()).isEqualTo(400);
    assertThat(imports.findById(pending)).isPresent();
  }

  @Test
  void connectsToNothingWithoutAnUploadedTicket() {
    UUID event = data.insertEvent("ALONE-" + suffix, "SCHEDULED");

    Response response = typeCode(ana, UUID.randomUUID(), "ALONE-" + suffix);

    assertThat(response.status().value()).isEqualTo(404);
    assertThat(response.body().get("code").asString()).isEqualTo("IMPORT_NOT_FOUND");
    assertThat(connections.isConnected(ana, event)).isFalse();
  }

  @Test
  void keepsAnImportOutOfReachOfAnotherParticipant() {
    UUID event = data.insertEvent("OWNED-" + suffix, "SCHEDULED");
    UUID pending = pendingImportOf(ana);

    Response response = typeCode(bruno, pending, "OWNED-" + suffix);

    assertThat(response.status().value()).isEqualTo(404);
    assertThat(response.body().get("code").asString()).isEqualTo("IMPORT_NOT_FOUND");
    assertThat(connections.isConnected(bruno, event)).isFalse();
    assertThat(imports.findById(pending)).isPresent();
  }

  @Test
  void refusesTheTypedCodeOfATicketAnotherAccountImportedAndDeletesTheImport() {
    UUID event = data.insertEvent("USED-" + suffix, "SCHEDULED");
    assertThat(
            upload(ana, TicketPdfs.ticketOfEvent("USED-" + suffix, entryCode))
                .body()
                .get("status")
                .asString())
        .isEqualTo("CONNECTED");
    UUID pending = pendingImportOf(bruno);

    Response response = typeCode(bruno, pending, "USED-" + suffix);

    assertThat(response.status().value()).isEqualTo(409);
    assertThat(response.body().get("code").asString()).isEqualTo("TICKET_ALREADY_IMPORTED");
    assertThat(connections.isConnected(bruno, event)).isFalse();
    assertThat(imports.findById(pending)).isEmpty();
  }

  @Test
  void matchesOnlyTicketedEventsThatHaveNotEndedOrBeenCancelled() {
    data.insertEvent("ENDED-" + suffix, "ENDED");
    data.insertEvent("CANCEL-" + suffix, "CANCELLED");
    data.insertPublicEvent("PUB-" + suffix);

    for (String code : new String[] {"ENDED-" + suffix, "CANCEL-" + suffix, "PUB-" + suffix}) {
      UUID pending = pendingImportOf(ana);

      assertThat(typeCode(ana, pending, code).body().get("code").asString())
          .isEqualTo("EVENT_NOT_ON_CROWDED");
    }
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_connection WHERE user_id = ?", Integer.class, ana))
        .isZero();
  }
}
