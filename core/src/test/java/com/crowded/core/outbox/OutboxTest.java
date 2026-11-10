package com.crowded.core.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.json.JsonMapper;

class OutboxTest {

  private final OutboxRepository messages = mock(OutboxRepository.class);
  private final JsonMapper json = JsonMapper.builder().build();
  private final Outbox outbox =
      new Outbox(messages, json, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

  @Test
  void storesANotificationRequestWithTheContractOfTheMessage() {
    UUID event = UUID.randomUUID();
    UUID cause = UUID.randomUUID();

    NotificationRequested message = outbox.requestNotification("OFFICIAL_CHANGE", event, cause);

    ArgumentCaptor<OutboxMessage> saved = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(messages).save(saved.capture());
    assertThat(saved.getValue().getId()).isEqualTo(message.messageId());
    assertThat(saved.getValue().getRoutingKey()).isEqualTo("notification.requested");
    assertThat(json.readTree(saved.getValue().getPayload()).propertyNames())
        .containsExactlyInAnyOrder("messageId", "type", "eventId", "causeId", "userIds");
    assertThat(json.readValue(saved.getValue().getPayload(), NotificationRequested.class))
        .isEqualTo(
            new NotificationRequested(
                message.messageId(), "OFFICIAL_CHANGE", event, cause, List.of()));
  }
}
