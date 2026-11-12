package com.crowded.core.outbox.infrastructure.messaging;

import com.crowded.core.outbox.domain.model.OutboxMessage;
import com.crowded.core.outbox.domain.repository.OutboxRepository;
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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Publishes the outbox to RabbitMQ, oldest first, and removes each message once the broker has
 * confirmed it. A message that fails stays in the outbox and is published again, with the same
 * message id, on the next run; consumers skip a message they already processed.
 */
@Component
class OutboxRelay {

  private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

  /** Messages per run; the rest wait for the next one. */
  static final int BATCH_SIZE = 100;

  private static final Duration CONFIRM_TIMEOUT = Duration.ofSeconds(5);

  private final OutboxRepository outbox;
  private final RabbitTemplate rabbit;

  OutboxRelay(OutboxRepository outbox, RabbitTemplate rabbit) {
    this.outbox = outbox;
    this.rabbit = rabbit;
  }

  /** Runs every half second, well inside the 1-minute delivery target. */
  @Scheduled(fixedDelay = 500)
  @Transactional
  public void publishWaiting() {
    for (OutboxMessage message : outbox.lockOldest(BATCH_SIZE)) {
      try {
        publish(message);
      } catch (AmqpException e) {
        // Keep the order: the rest waits for the next run.
        log.warn("Outbox message {} not published, retrying later", message.getId(), e);
        return;
      }
      outbox.remove(message.getId());
    }
  }

  private void publish(OutboxMessage message) {
    Message amqp =
        MessageBuilder.withBody(message.getPayload().getBytes(StandardCharsets.UTF_8))
            .setContentType(MessageProperties.CONTENT_TYPE_JSON)
            .setMessageId(message.getId().toString())
            .setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .build();
    rabbit.invoke(
        operations -> {
          operations.send(MessagingConfiguration.EXCHANGE, message.getRoutingKey(), amqp);
          operations.waitForConfirmsOrDie(CONFIRM_TIMEOUT.toMillis());
          return null;
        });
    log.info(
        "Published outbox message {} with routing key {}",
        message.getId(),
        message.getRoutingKey());
  }
}
