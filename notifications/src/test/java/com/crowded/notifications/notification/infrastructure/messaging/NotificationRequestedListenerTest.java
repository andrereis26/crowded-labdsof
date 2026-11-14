package com.crowded.notifications.notification.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.notifications.notification.application.dto.NotifyParticipantsCommand;
import com.crowded.notifications.notification.application.service.ParticipantNotifier;
import com.crowded.notifications.notification.domain.model.NotificationType;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class NotificationRequestedListenerTest {

  private final ParticipantNotifier notifier = mock(ParticipantNotifier.class);
  private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
  // Two seconds after the change was committed in Core.
  private final NotificationRequestedListener listener =
      new NotificationRequestedListener(
          notifier, meters, Clock.fixed(Instant.EPOCH.plusSeconds(2), ZoneOffset.UTC));
  private final NotifyParticipantsCommand message =
      new NotifyParticipantsCommand(
          UUID.randomUUID(),
          NotificationType.OFFICIAL_CHANGE,
          UUID.randomUUID(),
          UUID.randomUUID(),
          List.of());

  @Test
  void notifiesTheParticipantsAndMeasuresTheLatencyFromTheChangesCommit() {
    when(notifier.notify(message)).thenReturn(3);

    listener.onNotificationRequested(message, "1970-01-01T00:00:00Z");

    verify(notifier).notify(message);
    assertThat(latency().count()).isOne();
    assertThat(latency().totalTime(TimeUnit.SECONDS)).isEqualTo(2);
  }

  @Test
  void aRedeliveredMessageOrOneWithoutTheHeaderIsNotMeasured() {
    when(notifier.notify(message)).thenReturn(0, 1);

    listener.onNotificationRequested(message, "1970-01-01T00:00:00Z");
    listener.onNotificationRequested(message, null);

    assertThat(latency().count()).isZero();
  }

  @Test
  void anInvalidHeaderIsNotMeasuredAndDoesNotFailTheMessage() {
    when(notifier.notify(message)).thenReturn(1);

    listener.onNotificationRequested(message, "yesterday");

    assertThat(latency().count()).isZero();
  }

  private Timer latency() {
    return meters.get("crowded.notification.latency").timer();
  }
}
