package com.crowded.notifications.notification.infrastructure.messaging;

import com.crowded.notifications.notification.domain.repository.NotificationAnnouncements;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link NotificationAnnouncements} on a Redis channel that every instance of this service
 * subscribes to ({@link LiveNotificationConfiguration}). The message is the notification's id: each
 * instance reads the notification itself.
 */
@Component
class RedisNotificationAnnouncements implements NotificationAnnouncements {

  private static final Logger log = LoggerFactory.getLogger(RedisNotificationAnnouncements.class);

  private final StringRedisTemplate redis;

  RedisNotificationAnnouncements(StringRedisTemplate redis) {
    this.redis = redis;
  }

  @Override
  public void announce(UUID notificationId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              publish(notificationId);
            }
          });
    } else {
      publish(notificationId);
    }
  }

  private void publish(UUID notificationId) {
    try {
      redis.convertAndSend(LiveNotificationConfiguration.CHANNEL, notificationId.toString());
    } catch (RuntimeException e) {
      // Not sent live; the recipient finds it in the center.
      log.warn("Notification {} not announced: {}", notificationId, e.toString());
    }
  }
}
