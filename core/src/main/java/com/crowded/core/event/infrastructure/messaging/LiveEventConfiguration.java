package com.crowded.core.event.infrastructure.messaging;

import com.crowded.core.event.application.service.LiveEventPages;
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
 * Every instance subscribes to the announcements of changed events and sends the new page to the
 * live streams it holds (ADR 0007).
 */
@Configuration(proxyBeanMethods = false)
class LiveEventConfiguration {

  /** Redis channel of the ids of events whose page changed. */
  static final String CHANNEL = "core:events:changed";

  @Bean
  RedisMessageListenerContainer liveEventListener(
      RedisConnectionFactory redis, LiveEventPages live) {
    RedisMessageListenerContainer container = new RedisMessageListenerContainer();
    container.setConnectionFactory(redis);
    container.addMessageListener(new AnnouncementListener(live), new ChannelTopic(CHANNEL));
    return container;
  }

  /** Sends the announced event's page to this instance's streams. */
  static final class AnnouncementListener implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementListener.class);

    private final LiveEventPages live;

    AnnouncementListener(LiveEventPages live) {
      this.live = live;
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
      String body = new String(message.getBody(), StandardCharsets.UTF_8);
      try {
        live.deliver(UUID.fromString(body));
      } catch (RuntimeException e) {
        // The participant still reads the new page on the next load.
        log.warn("Update of event {} not sent live: {}", body, e.toString());
      }
    }
  }
}
