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
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * A member sends an operational alert through the API: Core stores and audits it, asks
 * Notifications to notify through the outbox, and the event's page shows it to its participants.
 */
@ResourceLock(IntegrationTest.SHARED_STATE)
class AlertIT extends IntegrationTest {

  private static final HttpClient HTTP = HttpClient.newHttpClient();
  private static final Instant START =
      Instant.now().plus(10, ChronoUnit.DAYS).truncatedTo(ChronoUnit.HOURS);
  private static final String PASSWORD_HASH = "{bcrypt}$2a$10$" + "a".repeat(53);

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private AmqpAdmin amqp;
  @Autowired private RabbitTemplate rabbit;
  @Autowired private JsonMapper json;

  private final UUID organization = UUID.randomUUID();
  private final UUID member = UUID.randomUUID();
  private final UUID outsider = UUID.randomUUID();
  private final UUID participant = UUID.randomUUID();
  private final UUID event = UUID.randomUUID();
  private final String queue = "alert-it-" + UUID.randomUUID();

  @BeforeEach
  void createAnEventWithAConnectedParticipant() {
    UUID otherOrganization = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO organization (id, name, created_at) VALUES (?, ?, now()), (?, ?, now())",
        organization,
        "Ribeira Alertas " + organization,
        otherOrganization,
        "Outra " + otherOrganization);
    jdbc.update(
        "INSERT INTO app_user (id, organization_id, email, password_hash, role, created_at, active, name)"
            + " VALUES (?, ?, ?, ?, 'ORGANIZATION_MEMBER', now(), true, 'Membro'),"
            + " (?, ?, ?, ?, 'ORGANIZATION_MEMBER', now(), true, 'Membro'),"
            + " (?, NULL, ?, ?, 'PARTICIPANT', now(), true, 'Ana')",
        member,
        organization,
        member + "@crowded.test",
        PASSWORD_HASH,
        outsider,
        otherOrganization,
        outsider + "@crowded.test",
        PASSWORD_HASH,
        participant,
        participant + "@crowded.test",
        PASSWORD_HASH);
    jdbc.update(
        "INSERT INTO event (id, organization_id, name, description, type, ticket_event_code,"
            + " start_date_time, doors_open_at, end_date_time, status, venue_name, address,"
            + " district, latitude, longitude)"
            + " VALUES (?, ?, 'Concerto', 'Teste', 'TICKETED', ?, ?, ?, ?, 'SCHEDULED',"
            + " 'Coliseu', 'Porto', 'Porto', 41.146, -8.606)",
        event,
        organization,
        "AL-" + event,
        Timestamp.from(START),
        Timestamp.from(START.minus(90, ChronoUnit.MINUTES)),
        Timestamp.from(START.plus(3, ChronoUnit.HOURS)));
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
  void deleteTheQueue() {
    amqp.deleteQueue(queue);
  }

  @Test
  void sendsTheAlertAuditsItAsksForNotificationsAndShowsItOnThePage() throws Exception {
    // The page is cached before the alert, so the alert must refresh it.
    assertThat(json.readTree(page(participant).body()).get("activeAlerts").isEmpty()).isTrue();

    HttpResponse<String> response =
        send(
            member,
            "{\"type\": \"ENTRANCE_CLOSURE\", \"message\": \"  Porta A fechada. Usem a B.  \"}");

    assertThat(response.statusCode()).isEqualTo(201);
    JsonNode sent = json.readTree(response.body());
    UUID alert = UUID.fromString(sent.get("id").asString());
    assertThat(sent.get("severity").asString()).isEqualTo("CRITICAL");
    assertThat(sent.get("message").asString()).isEqualTo("Porta A fechada. Usem a B.");
    assertThat(sent.get("recipientsCount").asInt()).isOne();
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT event_id, organization_id, type::text, severity::text, message, status::text,"
                + " sent_at, recipients_count FROM alert WHERE id = ?",
            alert);
    assertThat(row)
        .containsEntry("event_id", event)
        .containsEntry("organization_id", organization)
        .containsEntry("type", "ENTRANCE_CLOSURE")
        .containsEntry("severity", "CRITICAL")
        .containsEntry("message", "Porta A fechada. Usem a B.")
        .containsEntry("status", "SENT")
        .containsEntry("recipients_count", 1);
    assertThat(row.get("sent_at")).isNotNull();
    Map<String, Object> audit =
        jdbc.queryForMap(
            "SELECT actor_id, event_id, occurred_at FROM audit_log_entry"
                + " WHERE entity_id = ? AND action = 'ALERT_SENT'",
            alert);
    assertThat(audit).containsEntry("actor_id", member).containsEntry("event_id", event);
    assertThat(audit.get("occurred_at")).isNotNull();

    Message message = receiveFor(alert, 10_000);
    assertThat(message).isNotNull();
    JsonNode request = json.readTree(message.getBody());
    assertThat(request.get("type").asString()).isEqualTo("OPERATIONAL_ALERT");
    assertThat(request.get("eventId").asString()).isEqualTo(event.toString());

    JsonNode shown = json.readTree(page(participant).body()).get("activeAlerts");
    assertThat(shown.size()).isOne();
    assertThat(shown.get(0).get("id").asString()).isEqualTo(alert.toString());
    assertThat(shown.get(0).get("type").asString()).isEqualTo("ENTRANCE_CLOSURE");
    assertThat(shown.get(0).get("severity").asString()).isEqualTo("CRITICAL");
    assertThat(shown.get(0).get("message").asString()).isEqualTo("Porta A fechada. Usem a B.");
  }

  @Test
  void refusesAnotherOrganizationAMissingTypeOrMessageAndAClosedEvent() throws Exception {
    HttpResponse<String> outside = send(outsider, "{\"type\": \"OTHER\", \"message\": \"Olá.\"}");
    assertThat(outside.statusCode()).isEqualTo(403);
    assertThat(json.readTree(outside.body()).get("code").asString())
        .isEqualTo("NOT_EVENT_ORGANIZATION");
    assertThat(send(member, "{\"message\": \"Olá.\"}").statusCode()).isEqualTo(400);
    assertThat(send(member, "{\"type\": \"OTHER\", \"message\": \"  \"}").statusCode())
        .isEqualTo(400);

    jdbc.update("UPDATE event SET status = 'CANCELLED' WHERE id = ?", event);
    HttpResponse<String> closed = send(member, "{\"type\": \"OTHER\", \"message\": \"Olá.\"}");
    assertThat(closed.statusCode()).isEqualTo(409);
    assertThat(json.readTree(closed.body()).get("code").asString()).isEqualTo("EVENT_CLOSED");

    assertThat(
            jdbc.queryForObject(
                "SELECT count(*) FROM alert WHERE event_id = ?", Integer.class, event))
        .isZero();
  }

  /**
   * The next message about {@code alert}, or null. Messages of the other tests are skipped: the
   * relay can publish one after its test ended.
   */
  private Message receiveFor(UUID alert, long timeoutMillis) {
    long deadline = System.currentTimeMillis() + timeoutMillis;
    for (long left = timeoutMillis; left > 0; left = deadline - System.currentTimeMillis()) {
      Message message = rabbit.receive(queue, left);
      if (message != null
          && json.readTree(message.getBody()).get("causeId").asString().equals(alert.toString())) {
        return message;
      }
    }
    return null;
  }

  private HttpResponse<String> send(UUID user, String body)
      throws IOException, InterruptedException {
    return HTTP.send(
        HttpRequest.newBuilder(
                URI.create("http://localhost:" + port + "/events/" + event + "/alerts"))
            .header("Authorization", "Bearer " + token(user))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }

  private HttpResponse<String> page(UUID user) throws IOException, InterruptedException {
    return HTTP.send(
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/events/" + event))
            .header("Authorization", "Bearer " + token(user))
            .GET()
            .build(),
        HttpResponse.BodyHandlers.ofString());
  }
}
