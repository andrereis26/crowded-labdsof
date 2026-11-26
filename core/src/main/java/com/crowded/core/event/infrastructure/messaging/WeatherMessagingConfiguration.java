package com.crowded.core.event.infrastructure.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.boot.amqp.autoconfigure.RabbitListenerRetrySettingsCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Core's queue on the {@code crowded.events} exchange and its dead-letter queue (Asynchronous
 * Flows). A message that fails is retried after 5 s, 15 s and 30 s ({@code application.yml}), then
 * moved to the dead-letter queue; one that is not valid JSON goes there at once.
 */
@Configuration(proxyBeanMethods = false)
class WeatherMessagingConfiguration {

  private static final Logger log = LoggerFactory.getLogger(WeatherMessagingConfiguration.class);

  /** Queue of the messages Core consumes. */
  static final String QUEUE = "core";

  /** Where messages that keep failing wait for inspection and replay. */
  static final String DEAD_LETTER_QUEUE = QUEUE + ".dlq";

  /** Routing key of Jobs' {@code WeatherUpdated}. */
  static final String WEATHER_UPDATED = "weather.updated";

  @Bean
  Queue coreQueue() {
    // Rejected messages go through the default exchange to the dead-letter queue.
    return QueueBuilder.durable(QUEUE)
        .deadLetterExchange("")
        .deadLetterRoutingKey(DEAD_LETTER_QUEUE)
        .build();
  }

  @Bean
  Queue coreDeadLetterQueue() {
    return QueueBuilder.durable(DEAD_LETTER_QUEUE).build();
  }

  @Bean
  Binding weatherUpdatedBinding(Queue coreQueue, TopicExchange crowdedEvents) {
    return BindingBuilder.bind(coreQueue).to(crowdedEvents).with(WEATHER_UPDATED);
  }

  /** Messages are JSON, read into the listener's parameter type. */
  @Bean
  MessageConverter jsonMessageConverter() {
    return new JacksonJsonMessageConverter();
  }

  /**
   * After the last retry, the failure is logged as an error, with the message's trace id, and the
   * message is rejected without requeueing, so it is dead-lettered.
   */
  @Bean
  MessageRecoverer rejectToDeadLetterQueue() {
    return new RejectAndDontRequeueRecoverer() {
      @Override
      public void recover(Message message, Throwable cause) {
        log.error(
            "Message {} moved to the dead-letter queue",
            message.getMessageProperties().getMessageId(),
            cause);
        super.recover(message, cause);
      }
    };
  }

  @Bean
  RabbitListenerRetrySettingsCustomizer retryOnlyWhatCanSucceed() {
    return settings -> settings.setExceptionPredicate(WeatherMessagingConfiguration::isRetryable);
  }

  /** Whether retrying can help: not for a message that is not valid JSON. */
  static boolean isRetryable(Throwable failure) {
    for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
      if (cause instanceof MessageConversionException) {
        return false;
      }
    }
    return true;
  }
}
