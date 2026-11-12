package com.crowded.notifications.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.notifications.IntegrationTest;
import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.application.service.ParticipantNotifier;
import com.crowded.notifications.notification.domain.model.Notification;
import com.crowded.notifications.notification.domain.model.NotificationType;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Core's {@code NotificationRequested}, published to RabbitMQ as Core's outbox relay does, becomes
 * one notification per connected participant, once, and a message that keeps failing ends in the
 * dead-letter queue. Uses the demo data (database/seed/R__demo_data.sql).
 */
class NotificationRequestedIT extends IntegrationTest {

  // Rock no Parque, cancelled; Bruno has a ticket.
  private static final UUID ROCK = UUID.fromString("30000000-0000-4000-8000-000000000005");
  private static final UUID ROCK_CANCELLED =
      UUID.fromString("50000000-0000-4000-8000-000000000003");
  private static final UUID BRUNO = UUID.fromString("10000000-0000-4000-8000-000000000012");

  // Arraial de São João, a public event that moved; Carla connected with the access code.
  private static final UUID ARRAIAL = UUID.fromString("30000000-0000-4000-8000-000000000004");
  private static final UUID ARRAIAL_MOVED = UUID.fromString("50000000-0000-4000-8000-000000000002");
  private static final UUID CARLA = UUID.fromString("10000000-0000-4000-8000-000000000013");

  // Noite de Fado, postponed; Ana has a ticket, through connection ...0002.
  private static final UUID FADO = UUID.fromString("30000000-0000-4000-8000-000000000003");
  private static final UUID FADO_POSTPONED =
      UUID.fromString("50000000-0000-4000-8000-000000000001");
  private static final UUID ANA = UUID.fromString("10000000-0000-4000-8000-000000000011");
  private static final UUID ANA_FADO_TICKET =
      UUID.fromString("40000000-0000-4000-8000-000000000002");

  private static final Duration TIMEOUT = Duration.ofSeconds(20);

  @Autowired private RabbitTemplate rabbit;
  @Autowired private JdbcTemplate jdbc;
  @Autowired private ParticipantNotifier notifier;

  private final Set<String> deadLettered = new HashSet<>();

  @Test
  @ResourceLock(SHARED_STATE)
  void aTicketHolderIsNotifiedOfACancellationWithinAMinute() {
    UUID messageId = UUID.randomUUID();
    Instant published = Instant.now();

    publish(messageId, json(messageId, "OFFICIAL_CHANGE", ROCK, ROCK_CANCELLED));

    String key = Notification.deduplicationKey(BRUNO, messageId);
    await(() -> count(key) == 1);
    assertThat(Duration.between(published, Instant.now())).isLessThan(Duration.ofMinutes(1));
    Map<String, Object> row =
        jdbc.queryForMap(
            "SELECT event_id, CAST(type AS text) AS type, cause_id, title, body "
                + "FROM notification WHERE deduplication_key = ?",
            key);
    assertThat(row)
        .containsEntry("event_id", ROCK)
        .containsEntry("type", "OFFICIAL_CHANGE")
        .containsEntry("cause_id", ROCK_CANCELLED)
        .containsEntry("title", "Cancelado");
    assertThat((String) row.get("body"))
        .startsWith("A organização cancelou o evento. O teu bilhete deixa de ser válido.")
        .contains("Reembolso: Reembolso pedido até 5 DEZ em ribeira-live.test/reembolsos");
  }

  @Test
  @ResourceLock(SHARED_STATE)
  void aParticipantConnectedWithTheAccessCodeIsNotifiedWithoutTicketDetails() {
    UUID messageId = UUID.randomUUID();

    publish(messageId, json(messageId, "OFFICIAL_CHANGE", ARRAIAL, ARRAIAL_MOVED));

    String key = Notification.deduplicationKey(CARLA, messageId);
    await(() -> count(key) == 1);
    assertThat(
            jdbc.queryForMap(
                "SELECT title, body FROM notification WHERE deduplication_key = ?", key))
        .containsEntry("title", "Novo local: Jardim do Palácio de Cristal")
        .containsEntry("body", "Era em Praça da Ribeira.");
  }

  @Test
  @ResourceLock(SHARED_STATE)
  void aMessageDeliveredTwiceNotifiesOnce() {
    NotifyParticipantsCommand command =
        new NotifyParticipantsCommand(
            UUID.randomUUID(), NotificationType.OFFICIAL_CHANGE, ROCK, ROCK_CANCELLED, List.of());

    int first = notifier.notify(command);
    int second = notifier.notify(command);

    assertThat(first).isOne();
    assertThat(second).isZero();
    assertThat(count(Notification.deduplicationKey(BRUNO, command.messageId()))).isOne();
  }

  @Test
  @ResourceLock(SHARED_STATE)
  void aParticipantWhoTurnedOfficialChangesOffForTheEventIsNotNotified() {
    UUID setting = UUID.randomUUID();
    jdbc.update(
        "INSERT INTO event_notification_setting (id, event_connection_id, type, enabled) "
            + "VALUES (?, ?, 'OFFICIAL_CHANGE', false)",
        setting,
        ANA_FADO_TICKET);
    try {
      NotifyParticipantsCommand command =
          new NotifyParticipantsCommand(
              UUID.randomUUID(), NotificationType.OFFICIAL_CHANGE, FADO, FADO_POSTPONED, List.of());

      notifier.notify(command);

      assertThat(count(Notification.deduplicationKey(ANA, command.messageId()))).isZero();
    } finally {
      jdbc.update("DELETE FROM event_notification_setting WHERE id = ?", setting);
    }
  }

  @Test
  @ResourceLock(SHARED_STATE)
  void aMessageThatKeepsFailingEndsInTheDeadLetterQueue() {
    UUID messageId = UUID.randomUUID();

    // A change that does not exist: every retry fails.
    publish(messageId, json(messageId, "OFFICIAL_CHANGE", ROCK, UUID.randomUUID()));

    assertThat(deadLettered(messageId)).isTrue();
  }

  @Test
  @ResourceLock(SHARED_STATE)
  void aTypeWithoutTextAndInvalidJsonGoToTheDeadLetterQueue() {
    UUID weather = UUID.randomUUID();
    UUID invalid = UUID.randomUUID();

    publish(weather, json(weather, "WEATHER_WARNING", ROCK, UUID.randomUUID()));
    publish(invalid, "{\"messageId\": ");

    assertThat(deadLettered(weather)).isTrue();
    assertThat(deadLettered(invalid)).isTrue();
  }

  /** Publishes {@code body} as Core's outbox relay does. */
  private void publish(UUID messageId, String body) {
    Message message =
        MessageBuilder.withBody(body.getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setMessageId(messageId.toString())
            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .build();
    rabbit.send(
        MessagingConfiguration.EXCHANGE, MessagingConfiguration.NOTIFICATION_REQUESTED, message);
  }

  private static String json(UUID messageId, String type, UUID eventId, UUID causeId) {
    return """
        {"messageId": "%s", "type": "%s", "eventId": "%s", "causeId": "%s", "userIds": []}"""
        .formatted(messageId, type, eventId, causeId);
  }

  private int count(String deduplicationKey) {
    Integer count =
        jdbc.queryForObject(
            "SELECT count(*) FROM notification WHERE deduplication_key = ?",
            Integer.class,
            deduplicationKey);
    return count == null ? 0 : count;
  }

  /**
   * Whether the message reaches the dead-letter queue. The messages read meanwhile are kept, since
   * the queue's order is not the publishing order.
   */
  private boolean deadLettered(UUID messageId) {
    Instant deadline = Instant.now().plus(TIMEOUT);
    while (!deadLettered.contains(messageId.toString()) && Instant.now().isBefore(deadline)) {
      Message message = rabbit.receive(MessagingConfiguration.DEAD_LETTER_QUEUE, 1000);
      if (message != null) {
        deadLettered.add(message.getMessageProperties().getMessageId());
      }
    }
    return deadLettered.contains(messageId.toString());
  }

  private static void await(BooleanSupplier condition) {
    Instant deadline = Instant.now().plus(TIMEOUT);
    while (!condition.getAsBoolean()) {
      if (Instant.now().isAfter(deadline)) {
        throw new AssertionError("Not true within " + TIMEOUT);
      }
      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new AssertionError("Interrupted", e);
      }
    }
  }
}
