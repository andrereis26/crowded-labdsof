package com.crowded.notifications.notification.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RedisNotificationAnnouncementsTest {

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final RedisNotificationAnnouncements announcements =
      new RedisNotificationAnnouncements(redis);
  private final UUID id = UUID.randomUUID();

  @AfterEach
  void endTransaction() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void announcesAtOnceWithoutATransaction() {
    announcements.announce(id);

    verify(redis).convertAndSend(LiveNotificationConfiguration.CHANNEL, id.toString());
  }

  @Test
  void announcesOnlyAfterTheTransactionCommits() {
    TransactionSynchronizationManager.initSynchronization();

    announcements.announce(id);

    verifyNoInteractions(redis);
    for (TransactionSynchronization sync :
        TransactionSynchronizationManager.getSynchronizations()) {
      sync.afterCommit();
    }
    verify(redis).convertAndSend(LiveNotificationConfiguration.CHANNEL, id.toString());
  }

  @Test
  void aFailedAnnouncementIsNotAnError() {
    doThrow(new IllegalStateException("Redis is down"))
        .when(redis)
        .convertAndSend(anyString(), anyString());

    announcements.announce(id);

    verify(redis).convertAndSend(LiveNotificationConfiguration.CHANNEL, id.toString());
  }
}
