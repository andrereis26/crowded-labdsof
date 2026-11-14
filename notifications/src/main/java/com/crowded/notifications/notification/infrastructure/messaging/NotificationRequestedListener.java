package com.crowded.notifications.notification.infrastructure.messaging;

import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.application.service.ParticipantNotifier;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumes Core's {@code NotificationRequested}. The message is acknowledged only after its
 * notifications are committed; a failure is retried, then dead-lettered ({@link
 * MessagingConfiguration}). Measures the notification latency from the change's commit in Core,
 * which its outbox relay sends in the {@code created-at} header.
 */
@Component
class NotificationRequestedListener {

  private static final Logger log = LoggerFactory.getLogger(NotificationRequestedListener.class);

  /** Header with the commit time in Core of the change behind the message. */
  static final String CREATED_AT = "created-at";

  private final ParticipantNotifier notifier;
  private final Clock clock;
  private final Timer latency;

  NotificationRequestedListener(ParticipantNotifier notifier, MeterRegistry meters, Clock clock) {
    this.notifier = notifier;
    this.clock = clock;
    this.latency =
        Timer.builder("crowded.notification.latency")
            .description("Time from a change's commit in Core until its notifications were stored")
            .publishPercentileHistogram()
            .register(meters);
  }

  @RabbitListener(queues = MessagingConfiguration.QUEUE)
  void onNotificationRequested(
      NotifyParticipantsCommand message,
      @Header(name = CREATED_AT, required = false) String createdAt) {
    // A redelivered message stores nothing new: it says nothing about the latency.
    if (notifier.notify(message) > 0 && createdAt != null) {
      record(message, createdAt);
    }
  }

  private void record(NotifyParticipantsCommand message, String createdAt) {
    try {
      latency.record(Duration.between(Instant.parse(createdAt), clock.instant()));
    } catch (DateTimeParseException e) {
      log.warn("Message {} has an invalid {} header", message.messageId(), CREATED_AT);
    }
  }
}
