package com.crowded.core.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

class OutboxRelayTest {

  private final OutboxRepository messages = mock(OutboxRepository.class);
  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
  private final RabbitOperations operations = mock(RabbitOperations.class);
  private final OutboxRelay relay = new OutboxRelay(messages, rabbit);

  private final OutboxMessage first = message("{\"n\":1}");
  private final OutboxMessage second = message("{\"n\":2}");

  OutboxRelayTest() {
    when(rabbit.invoke(any()))
        .thenAnswer(
            call ->
                call.<RabbitOperations.OperationsCallback<?>>getArgument(0).doInRabbit(operations));
  }

  @Test
  void publishesTheWaitingMessagesAndDeletesThemOnceConfirmed() {
    when(messages.lockOldest(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));

    relay.publishWaiting();

    ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
    verify(operations, times(2))
        .send(eq("crowded.events"), eq("notification.requested"), sent.capture());
    Message published = sent.getAllValues().getFirst();
    assertThat(new String(published.getBody(), StandardCharsets.UTF_8)).isEqualTo("{\"n\":1}");
    assertThat(published.getMessageProperties().getMessageId()).isEqualTo(first.getId().toString());
    assertThat(published.getMessageProperties().getContentType()).isEqualTo("application/json");
    assertThat(published.getMessageProperties().getDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
    verify(operations, times(2)).waitForConfirmsOrDie(anyLong());
    verify(messages).delete(first);
    verify(messages).delete(second);
  }

  @Test
  void keepsAMessageAndTheOnesAfterItWhenTheBrokerFails() {
    when(messages.lockOldest(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
    doThrow(new AmqpIOException(new IOException("broker down")))
        .when(operations)
        .send(anyString(), anyString(), any(Message.class));

    relay.publishWaiting();

    verify(messages, never()).delete(any());
    verify(operations).send(anyString(), anyString(), any(Message.class));
  }

  private static OutboxMessage message(String payload) {
    return new OutboxMessage(UUID.randomUUID(), "notification.requested", payload, Instant.EPOCH);
  }
}
