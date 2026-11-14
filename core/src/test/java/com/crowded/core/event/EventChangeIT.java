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
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A member publishes a change through the API: Core records it once, updates the event, audits it
 * and publishes {@code NotificationRequested} through the outbox to RabbitMQ.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
@ExtendWith(OutputCaptureExtension.class)
class EventChangeIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final Instant START =
      Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);

  // The trace a client or the router started, which the request continues.
  private static final String TRACE_ID = "4bf92f3577b34da6a3ce929d0e0e4736";

  // Same shape as the demo hashes (DatabaseIT checks every user's); no one signs in with it.
  private static final String PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AmqpAdmin amqp;
  @Autowired private RabbitTemplate rabbit;
  @Autowired private JsonMapper json;

  private final UUID organization = UUID.randomUUID();
  private final UUID member = UUID.randomUUID();
  private final UUID outsider = UUID.randomUUID();
  private final UUID deactivated = UUID.randomUUID();
  private final UUID event = UUID.randomUUID();
  private final String queue = "event-change-it-" + UUID.randomUUID();

  @BeforeEach
  void createAnOrganizationWithAnEvent() {
    UUID otherOrganization = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now()), (?, ?, now())",
        organization,
        "Ribeira Teste " + organization,
        otherOrganization,
        "Outra " + otherOrganization);
    jdbc.update(
        "INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at, active, name)"
            + " VALUES (?, ?, ?, ?, 'ORGANIZATION_MEMBER', now(), true, 'Membro'),"
            + " (?, ?, ?, ?, 'ORGANIZATION_MEMBER', now(), true, 'Membro'),"
            + " (?, ?, ?, ?, 'ORGANIZATION_MEMBER', now(), false, 'Membro')",
        member,
        organization,
        member + "@crowded.test",
        PASSWORD_HASH,
        outsider,
        otherOrganization,
        outsider + "@crowded.test",
        PASSWORD_HASH,
        deactivated,
        organization,
        deactivated + "@crowded.test",
        PASSWORD_HASH);
    jdbc.update(
        "INSERT INTO event (id, organization_id, name, description, type, ticket_event_code,"
            + " start_date_time, doors_open_at, end_date_time, status, venue_name, address,"
            + " district, latitude, longitude)"
            + " VALUES (?, ?, 'Concerto', 'Teste', 'TICKETED', ?, ?, ?, ?, 'SCHEDULED',"
            + " 'Coliseu', 'Porto', 'Porto', 41.146, -8.606)",
        event,
        organization,
        "IT-" + event,
        Timestamp.from(START),
        Timestamp.from(START.minus(90, ChronoUnit.MINUTES)),
        Timestamp.from(START.plus(3, ChronoUnit.HOURS)));
    TopicExchange exchange = new TopicExchange("crowded.events", true, false);
    amqp.declareExchange(exchange);
    Queue listener = new Queue(queue, false, false, false);
    amqp.declareQueue(listener);
    Binding binding = BindingBuilder.bind(listener).to(exchange).with("notification.requested");
    amqp.declareBinding(binding);
  }

  @AfterEach
  void deleteTheQueue() {
    amqp.deleteQueue(queue);
  }

  @Test
  void recordsTheCancellationOnceAndAsksForNotifications(CapturedOutput output) throws Exception {
    String body =
        """
        {"type": "CANCELLATION", "ticketStillValid": false,
         "refundInfo": "Pedir até 5 DEZ na bilheteira", "message": "Lamentamos."}
        """;

    HttpResponse<String> response = post(member, body);

    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode published = json.readTree(response.body());
    assertThat(published.get("severity").asString()).isEqualTo("CRITICAL");
    UUID change = UUID.fromString(published.get("changeId").asString());

    assertThat(
            jdbc.queryForObject("SELECT status::text FROM event WHERE id = ?", String.class, event))
        .isEqualTo("CANCELLED");
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT type::text, severity::text, new_value::text, ticket_still_valid, refund_info,"
                + " message, provenance_source_type::text, provenance_source_name"
                + " FROM event_change WHERE id = ?",
            change);
    assertThat(row)
        .containsEntry("type", "CANCELLATION")
        .containsEntry("severity", "CRITICAL")
        .containsEntry("new_value", "{\"status\": \"CANCELLED\"}")
        .containsEntry("ticket_still_valid", false)
        .containsEntry("refund_info", "Pedir até 5 DEZ na bilheteira")
        .containsEntry("message", "Lamentamos.")
        .containsEntry("provenance_source_type", "ORGANIZATION")
        .containsEntry("provenance_source_name", "Ribeira Teste " + organization);
    assertThat(
            jdbc.queryForObject(
                "SELECT actor_id FROM audit_log_entry WHERE entity_id = ?"
                    + " AND action = 'EVENT_CHANGE_PUBLISHED'",
                UUID.class,
                change))
        .isEqualTo(member);

    Message message = receiveFor(change, 10_000);
    assertThat(message).isNotNull();
    JsonNode notification = json.readTree(new String(message.getBody(), StandardCharsets.UTF_8));
    assertThat(notification.get("messageId").asString())
        .isEqualTo(message.getMessageProperties().getMessageId());
    assertThat(notification.get("type").asString()).isEqualTo("OFFICIAL_CHANGE");
    assertThat(notification.get("eventId").asString()).isEqualTo(event.toString());
    assertThat(notification.get("causeId").asString()).isEqualTo(change.toString());
    assertThat(notification.get("userIds").isEmpty()).isTrue();
    // In the request's trace, so Notifications logs it under the same trace id.
    assertThat(message.getMessageProperties().<String>getHeader("traceparent"))
        .startsWith("00-" + TRACE_ID + "-");
    assertThat(Instant.parse(message.getMessageProperties().<String>getHeader("created-at")))
        .isBefore(Instant.now());
    assertThat(output.getOut().lines())
        .filteredOn(
            line ->
                line.contains(
                    "Published outbox message " + notification.get("messageId").asString()))
        .singleElement()
        .satisfies(line -> assertThat(json.readTree(line).toString()).contains(TRACE_ID));
    // The relay deletes the row when it commits, just after the broker confirmed it.
    assertThat(waitForOutboxWithout(change)).isTrue();

    HttpResponse<String> again = post(member, body);

    assertThat(again.statusCode()).isEqualTo(200);
    assertThat(json.readTree(again.body()).get("changeId").asString()).isEqualTo(change.toString());
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_change WHERE event_id = ?", Integer.class, event))
        .isOne();
    assertThat(receiveFor(change, 1_500)).isNull();
  }

  @Test
  void postponesTheEventWithItsDoorsAndEnd() throws Exception {
    Instant newStart = START.plus(7, ChronoUnit.DAYS);

    HttpResponse<String> response =
        post(
            member,
            "{\"type\": \"POSTPONEMENT\", \"newValue\": {\"startDateTime\": \""
                + newStart
                + "\"}, \"ticketStillValid\": true}");

    assertThat(response.statusCode()).isEqualTo(201);
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT status::text, start_date_time, doors_open_at, end_date_time"
                + " FROM event WHERE id = ?",
            event);
    assertThat(row.get("status")).isEqualTo("POSTPONED");
    assertThat(((Timestamp) row.get("start_date_time")).toInstant()).isEqualTo(newStart);
    assertThat(((Timestamp) row.get("doors_open_at")).toInstant())
        .isEqualTo(newStart.minus(90, ChronoUnit.MINUTES));
    assertThat(((Timestamp) row.get("end_date_time")).toInstant())
        .isEqualTo(newStart.plus(3, ChronoUnit.HOURS));
  }

  @Test
  void refusesAMemberOfAnotherOrganization() throws Exception {
    HttpResponse<String> response = post(outsider, "{\"type\": \"CANCELLATION\"}");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(json.readTree(response.body()).get("code").asString())
        .isEqualTo("NOT_EVENT_ORGANIZATION");
    assertThat(
            jdbc.queryForObject("SELECT status::text FROM event WHERE id = ?", String.class, event))
        .isEqualTo("SCHEDULED");
    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM event_change WHERE event_id = ?", Integer.class, event))
        .isZero();
  }

  @Test
  void refusesADeactivatedMemberOfTheOrganization() throws Exception {
    HttpResponse<String> response = post(deactivated, "{\"type\": \"CANCELLATION\"}");

    assertThat(response.statusCode()).isEqualTo(403);
    assertThat(json.readTree(response.body()).get("code").asString())
        .isEqualTo("NOT_EVENT_ORGANIZATION");
    assertThat(
            jdbc.queryForObject("SELECT status::text FROM event WHERE id = ?", String.class, event))
        .isEqualTo("SCHEDULED");
  }

  @Test
  void movesTheEventToANewVenueWithItsLocation() throws Exception {
    HttpResponse<String> nowhere =
        post(
            member,
            "{\"type\": \"VENUE_CHANGE\", \"newValue\": {\"venueName\": \"Hard Club\","
                + " \"address\": \"Mercado Ferreira Borges\"}}");
    HttpResponse<String> moved =
        post(
            member,
            "{\"type\": \"VENUE_CHANGE\", \"newValue\": {\"venueName\": \"Hard Club\","
                + " \"address\": \"Mercado Ferreira Borges\", \"latitude\": 41.141,"
                + " \"longitude\": -8.614}}");

    assertThat(nowhere.statusCode()).isEqualTo(400);
    assertThat(json.readTree(nowhere.body()).get("code").asString()).isEqualTo("LOCATION_REQUIRED");
    assertThat(moved.statusCode()).isEqualTo(201);
    assertThat(
            jdbc.queryForObject(
                "SELECT status::text || '/' || venue_name || '/' || latitude FROM event"
                    + " WHERE id = ?",
                String.class,
                event))
        .isEqualTo("RELOCATED/Hard Club/41.141");
  }

  @Test
  void refusesAnInvalidChangeAsAProblem() throws Exception {
    HttpResponse<String> response =
        post(member, "{\"type\": \"VENUE_CHANGE\", \"newValue\": {\"venueName\": \"Hard Club\"}}");

    assertThat(response.statusCode()).isEqualTo(400);
    assertThat(response.headers().firstValue("Content-Type")).hasValue("application/problem+json");
    assertThat(json.readTree(response.body()).get("code").asString()).isEqualTo("VENUE_REQUIRED");
  }

  /**
   * The next message about {@code change}, or null. Messages of the other tests are skipped: the
   * relay can publish one after its test ended.
   */
  private Message receiveFor(UUID change, long timeoutMillis) {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    for (long left = timeoutMillis; left > 0; left = deadline - System.currentTimeMillis()) {
      Message message = rabbit.receive(queue, left);
      if (message != null
          && json.readTree(message.getBody()).get("causeId").asString().equals(change.toString())) {
        return message;
      }
    }
    return null;
  }

  private boolean waitForOutboxWithout(UUID change) throws InterruptedException {
    for (int attempt = 0; attempt < 50; attempt++) {
      Integer waiting =
          jdbc.queryForObject(
              "SELECT count(*) FROM outbox WHERE payload->>'causeId' = ?",
              Integer.class,
              change.toString());
      if (waiting != null && waiting == 0) {
        return true;
      }
      Thread.sleep(100);
    }
    return false;
  }

  private HttpResponse<String> post(UUID user, String body)
      throws IOException, InterruptedException {
    return HTTP.send(
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/events/" + event + "/changes"))
            .header("Authorization", "Bearer " + token(user))
            .header("Content-Type", "application/json")
            .header("traceparent", "00-" + TRACE_ID + "-00f067aa0ba902b7-01")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
