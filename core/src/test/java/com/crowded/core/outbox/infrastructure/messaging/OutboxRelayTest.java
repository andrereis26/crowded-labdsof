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

import com.crowded.core.TestTracing;
import com.crowded.core.outbox.domain.model.OutboxMessage;
import com.crowded.core.outbox.domain.repository.OutboxRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
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
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  // Three seconds after the messages were written.
  private final OutboxRelay relay =
      new OutboxRelay(
          outbox,
          rabbit,
          TestTracing.tracer(),
          TestTracing.propagator(),
          meters,
          Clock.fixed(Instant.EPOCH.plusSeconds(3), ZoneOffset.UTC));

  private final OutboxMessage first = message("{\"n\":1}", null);
  private final OutboxMessage second = message("{\"n\":2}", null);

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
    assertThat(published.getMessageProperties().<String>getHeader("created-at"))
        .isEqualTo("1970-01-01T00:00:00Z");
    verify(operations, times(2)).waitForConfirmsOrDie(anyLong());
    verify(outbox).remove(first.getId());
    verify(outbox).remove(second.getId());
    assertThat(meters.get("crowded.outbox.lag").timer().count()).isEqualTo(2);
    assertThat(meters.get("crowded.outbox.lag").timer().max(TimeUnit.SECONDS)).isEqualTo(3);
    assertThat(meters.get("crowded.outbox.waiting").gauge().value()).isZero();
  }

  @Test
  void publishesAMessageInTheTraceOfTheRequestThatWroteIt() {
    String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
    OutboxMessage traced = message("{}", "00-" + traceId + "-00f067aa0ba902b7-01");
    when(outbox.lockOldest(OutboxRelay.BATCH_SIZE)).thenReturn(List.of(traced));

    relay.publishWaiting();

    ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
    verify(operations).send(anyString(), anyString(), sent.capture());
    assertThat(sent.getValue().getMessageProperties().<String>getHeader("traceparent"))
        .startsWith("00-" + traceId + "-")
        .doesNotContain("00f067aa0ba902b7");
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
    assertThat(meters.get("crowded.outbox.waiting").gauge().value()).isEqualTo(3);
  }

  private static OutboxMessage message(String payload, String traceParent) {
    return OutboxMessage.restore(
        UUID.randomUUID(), "notification.requested", payload, Instant.EPOCH, traceParent);
  }
}
