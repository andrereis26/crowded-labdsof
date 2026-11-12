package com.crowded.notifications.notification.infrastructure.messaging;

import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.application.service.ParticipantNotifier;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Consumes Core's {@code NotificationRequested}. The message is acknowledged only after its
 * notifications are committed; a failure is retried, then dead-lettered ({@link
 * MessagingConfiguration}).
 */
@Component
class NotificationRequestedListener {

  private final ParticipantNotifier notifier;

  NotificationRequestedListener(ParticipantNotifier notifier) {
    this.notifier = notifier;
  }

  @RabbitListener(queues = MessagingConfiguration.QUEUE)
  void onNotificationRequested(NotifyParticipantsCommand message) {
    notifier.notify(message);
  }
}
