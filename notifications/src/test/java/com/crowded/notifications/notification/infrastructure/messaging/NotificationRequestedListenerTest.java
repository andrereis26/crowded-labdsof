package com.crowded.notifications.notification.infrastructure.messaging;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.application.service.ParticipantNotifier;
import com.crowded.notifications.notification.domain.model.NotificationType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationRequestedListenerTest {

  @Test
  void notifiesTheParticipantsTheMessageAsksFor() {
    ParticipantNotifier notifier = mock(ParticipantNotifier.class);
    NotifyParticipantsCommand message =
        new NotifyParticipantsCommand(
            UUID.randomUUID(),
            NotificationType.OFFICIAL_CHANGE,
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of());

    new NotificationRequestedListener(notifier).onNotificationRequested(message);

    verify(notifier).notify(message);
  }
}
