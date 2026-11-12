package com.crowded.core.outbox.infrastructure.messaging;

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

import com.crowded.core.outbox.domain.model.OutboxMessage;
import com.crowded.core.outbox.domain.repository.OutboxRepository;
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

  private final OutboxRepository outbox = mock(OutboxRepository.class);
  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
  private final RabbitOperations operations = mock(RabbitOperations.class);
  private final OutboxRelay relay = new OutboxRelay(outbox, rabbit);

  private final OutboxMessage first = message("{\"n\":1}");
  private final OutboxMessage second = message("{\"n\":2}");

  OutboxRelayTest() {
    when(rabbit.invoke(any()))
        .thenAnswer(
            call ->
                call.<RabbitOperations.OperationsCallback<?>>getArgument(0).doInRabbit(operations));
  }

  @Test
  void publishesTheWaitingMessagesAndRemovesThemOnceConfirmed() {
    when(outbox.lockOldest(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));

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
    verify(outbox).remove(first.getId());
    verify(outbox).remove(second.getId());
  }

  @Test
  void keepsAMessageAndTheOnesAfterItWhenTheBrokerFails() {
    when(outbox.lockOldest(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(first, second));
    doThrow(new AmqpIOException(new IOException("broker down")))
        .when(operations)
        .send(anyString(), anyString(), any(Message.class));

    relay.publishWaiting();

    verify(outbox, never()).remove(any());
    verify(operations).send(anyString(), anyString(), any(Message.class));
  }

  private static OutboxMessage message(String payload) {
    return OutboxMessage.restore(
        UUID.randomUUID(), "notification.requested", payload, Instant.EPOCH);
  }
}
