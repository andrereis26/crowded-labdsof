package com.crowded.core.event.infrastructure.messaging;

import com.crowded.core.event.application.service.EventPages;
import com.crowded.core.event.domain.repository.EventUpdates;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * {@link EventUpdates} through Redis: after the commit, drops the cached page, which every instance
 * shares, then announces the event's id on a channel that every instance subscribes to ({@link
 * LiveEventConfiguration}). Doing both after the commit keeps another request from caching the page
 * as it was before.
 */
@Component
class RedisEventUpdates implements EventUpdates {

  private static final Logger log = LoggerFactory.getLogger(RedisEventUpdates.class);

  private final CacheManager caches;
  private final StringRedisTemplate redis;

  RedisEventUpdates(CacheManager caches, StringRedisTemplate redis) {
    this.caches = caches;
    this.redis = redis;
  }

  @Override
  public void announce(UUID eventId) {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.registerSynchronization(
          new TransactionSynchronization() {
            @Override
            public void afterCommit() {
              publish(eventId);
            }
          });
    } else {
      publish(eventId);
    }
  }

  private void publish(UUID eventId) {
    try {
      Cache pages = caches.getCache(EventPages.CACHE);
      if (pages != null) {
        pages.evict(eventId);
      }
      redis.convertAndSend(LiveEventConfiguration.CHANNEL, eventId.toString());
    } catch (RuntimeException e) {
      // The page expires from the cache on its own; open streams get it on their next change.
      log.warn("Update of event {} not announced: {}", eventId, e.toString());
    }
  }
}
