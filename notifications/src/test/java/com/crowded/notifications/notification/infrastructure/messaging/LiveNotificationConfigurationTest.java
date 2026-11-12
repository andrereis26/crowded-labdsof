package com.crowded.notifications.notification.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.crowded.notifications.notification.application.service.LiveNotifications;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;

class LiveNotificationConfigurationTest {

  private final LiveNotifications live = mock(LiveNotifications.class);
  private final LiveNotificationConfiguration.AnnouncementListener listener =
      new LiveNotificationConfiguration.AnnouncementListener(live);

  @Test
  void deliversTheAnnouncedNotification() {
    UUID id = UUID.randomUUID();

    listener.onMessage(message(id.toString()), null);

    verify(live).deliver(id);
  }

  @Test
  void ignoresAnAnnouncementItCannotReadOrDeliver() {
    listener.onMessage(message("not an id"), null);
    verifyNoInteractions(live);

    doThrow(new IllegalStateException("database is down")).when(live).deliver(any());
    listener.onMessage(message(UUID.randomUUID().toString()), null);
  }

  private static DefaultMessage message(String body) {
    return new DefaultMessage(
        LiveNotificationConfiguration.CHANNEL.getBytes(StandardCharsets.UTF_8),
        body.getBytes(StandardCharsets.UTF_8));
  }
}
