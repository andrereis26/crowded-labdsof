package com.crowded.notifications.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import com.crowded.notifications.notification.domain.exception.CauseNotFoundException;
import com.crowded.notifications.notification.domain.exception.UnsupportedNotificationTypeException;
import com.crowded.notifications.notification.domain.model.NotificationType;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.retry.RejectAndDontRequeueRecoverer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConversionException;
import org.springframework.boot.retry.RetryPolicySettings;
import org.springframework.dao.QueryTimeoutException;

class MessagingConfigurationTest {

  private final MessagingConfiguration configuration = new MessagingConfiguration();

  @Test
  void bindsADurableQueueThatDeadLettersToItsOwnDeadLetterQueue() {
    Queue queue = configuration.notificationsQueue();
    Queue deadLetters = configuration.notificationsDeadLetterQueue();
    TopicExchange exchange = configuration.crowdedEvents();
    Binding binding = configuration.notificationRequestedBinding(queue, exchange);

    assertThat(queue.isDurable()).isTrue();
    assertThat(queue.getArguments())
        .containsEntry("x-dead-letter-exchange", "")
        .containsEntry("x-dead-letter-routing-key", "notifications.dlq");
    assertThat(deadLetters.getName()).isEqualTo("notifications.dlq");
    assertThat(deadLetters.isDurable()).isTrue();
    assertThat(exchange.getName()).isEqualTo("crowded.events");
    assertThat(binding.getDestination()).isEqualTo("notifications");
    assertThat(binding.getExchange()).isEqualTo("crowded.events");
    assertThat(binding.getRoutingKey()).isEqualTo("notification.requested");
  }

  @Test
  void rejectsWithoutRequeueingAfterTheLastRetrySoTheMessageIsDeadLettered() {
    assertThat(configuration.rejectToDeadLetterQueue())
        .isInstanceOf(RejectAndDontRequeueRecoverer.class);
    assertThat(configuration.jsonMessageConverter())
        .isInstanceOf(JacksonJsonMessageConverter.class);
  }

  @Test
  void theRetrySettingsSkipWhatCannotPass() {
    RetryPolicySettings settings = new RetryPolicySettings();

    configuration.retryOnlyWhatCanSucceed().customize(settings);

    assertThat(
            settings
                .getExceptionPredicate()
                .test(new UnsupportedNotificationTypeException(NotificationType.RECOGNITION)))
        .isFalse();
  }

  @Test
  void retriesFailuresThatCanPass() {
    assertThat(MessagingConfiguration.isRetryable(new QueryTimeoutException("slow database")))
        .isTrue();
    assertThat(
            MessagingConfiguration.isRetryable(
                new IllegalStateException(
                    "listener failed", new CauseNotFoundException(UUID.randomUUID(), null))))
        .isTrue();
  }

  @Test
  void deadLettersAtOnceWhatCannotPass() {
    assertThat(
            MessagingConfiguration.isRetryable(
                new IllegalStateException(
                    "listener failed",
                    new UnsupportedNotificationTypeException(NotificationType.RECOGNITION))))
        .isFalse();
    assertThat(MessagingConfiguration.isRetryable(new MessageConversionException("not JSON")))
        .isFalse();
  }
}
