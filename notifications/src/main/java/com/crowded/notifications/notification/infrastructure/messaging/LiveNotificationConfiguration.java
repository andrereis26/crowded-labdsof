package com.crowded.notifications.notification.infrastructure.messaging;

import com.crowded.notifications.notification.application.service.LiveNotifications;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Every instance subscribes to the announcements of new notifications and sends each one to the
 * live streams it holds (ADR 0007).
 */
@Configuration(proxyBeanMethods = false)
class LiveNotificationConfiguration {

  /** Redis channel of the ids of new notifications. */
  static final String CHANNEL = "notifications:created";

  @Bean
  RedisMessageListenerContainer liveNotificationListener(
      RedisConnectionFactory redis, LiveNotifications live) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(redis);
    container.addMessageListener(new AnnouncementListener(live), new ChannelTopic(CHANNEL));
    return container;
  }

  /** Delivers the announced notification to this instance's streams. */
  static final class AnnouncementListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementListener.class);

    private final LiveNotifications live;

    AnnouncementListener(LiveNotifications live) {
      this.live = live;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
      String body = new String(message.getBody(), StandardCharsets.UTF_8);
      try {
        live.deliver(UUID.fromString(body));
      } catch (RuntimeException e) {
        // Not sent live; the recipient finds it in the center.
        log.warn("Announcement {} not delivered: {}", body, e.toString());
      }
    }
  }
}
