package com.crowded.jobs.weather.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.jobs.weather.domain.model.WeatherUpdated;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpIOException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.core.RabbitOperations;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import tools.jackson.databind.json.JsonMapper;

/** WeatherUpdated leaves as persistent JSON, and counts only once the broker confirmed it. */
class RabbitWeatherUpdatesTest {

  private static final WeatherUpdated MESSAGE = new WeatherUpdated("Porto", UUID.randomUUID());

  private final RabbitTemplate rabbit = mock(RabbitTemplate.class);
  private final RabbitOperations operations = mock(RabbitOperations.class);
  private final RabbitWeatherUpdates updates =
      new RabbitWeatherUpdates(rabbit, JsonMapper.builder().build());

  @Test
  void publishesTheMessageWithTheSnapshotIdAndWaitsForTheConfirmation() {
    when(rabbit.invoke(any()))
        .thenAnswer(
            call ->
                call.<RabbitOperations.OperationsCallback<?>>getArgument(0).doInRabbit(operations));

    assertThat(updates.publish(MESSAGE)).isTrue();

    ArgumentCaptor<Message> sent = ArgumentCaptor.forClass(Message.class);
    verify(operations).send(eq("crowded.events"), eq("weather.updated"), sent.capture());
    verify(operations).waitForConfirmsOrDie(anyLong());
    assertThat(new String(sent.getValue().getBody(), StandardCharsets.UTF_8))
        .isEqualTo("{\"district\":\"Porto\",\"snapshotId\":\"" + MESSAGE.snapshotId() + "\"}");
    assertThat(sent.getValue().getMessageProperties().getMessageId())
        .isEqualTo(MESSAGE.snapshotId().toString());
    assertThat(sent.getValue().getMessageProperties().getDeliveryMode())
        .isEqualTo(MessageDeliveryMode.PERSISTENT);
  }

  @Test
  void saysSoWhenTheBrokerDidNotConfirm() {
    doThrow(new AmqpIOException(new java.io.IOException("down"))).when(rabbit).invoke(any());

    assertThat(updates.publish(MESSAGE)).isFalse();
  }
}
