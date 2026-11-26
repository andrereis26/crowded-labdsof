package com.crowded.jobs.weather.infrastructure.messaging;

import com.crowded.jobs.weather.domain.model.WeatherUpdated;
import com.crowded.jobs.weather.domain.repository.WeatherUpdates;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Publishes {@link WeatherUpdated} as JSON with the snapshot's id as message id, and waits for the
 * broker to confirm it, so an unconfirmed snapshot is published again on the next run.
 */
@Component
class RabbitWeatherUpdates implements WeatherUpdates {

  private static final Logger log = LoggerFactory.getLogger(RabbitWeatherUpdates.class);

  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

  private final RabbitTemplate rabbit;
  private final JsonMapper json;

  RabbitWeatherUpdates(RabbitTemplate rabbit, JsonMapper json) {
    this.rabbit = rabbit;
    this.json = json;
  }

  @Override
  public boolean publish(WeatherUpdated message) {
    MessageProperties properties = new MessageProperties();
    properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
    properties.setMessageId(message.snapshotId().toString());
    properties.setDeliveryMode(MessageDeliveryMode.PERSISTENT);
    Message amqp =
        MessageBuilder.withBody(json.writeValueAsString(message).getBytes(StandardCharsets.UTF_8))
            .andProperties(properties)
            .build();
    try {
      rabbit.invoke(
          operations -> {
            operations.send(MessagingConfiguration.EXCHANGE, WeatherUpdated.ROUTING_KEY, amqp);
            operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
            return null;
          });
      log.info(
          "Published WeatherUpdated for {}, snapshot {}", message.district(), message.snapshotId());
      return true;
    } catch (AmqpException e) {
      log.warn(
          "WeatherUpdated for snapshot {} not published, retrying later", message.snapshotId(), e);
      return false;
    }
  }
}
