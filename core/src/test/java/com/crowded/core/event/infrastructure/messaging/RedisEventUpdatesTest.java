package com.crowded.core.event.infrastructure.messaging;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.event.application.service.EventPages;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class RedisEventUpdatesTest {

  private static final UUID EVENT = UUID.randomUUID();

  private final CacheManager caches = mock(CacheManager.class);
  private final Cache pages = mock(Cache.class);
  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);
  private final RedisEventUpdates updates = new RedisEventUpdates(caches, redis);

  @AfterEach
  void endTheTransaction() {
    if (TransactionSynchronizationManager.isSynchronizationActive()) {
      TransactionSynchronizationManager.clearSynchronization();
    }
  }

  @Test
  void dropsTheCachedPageAndAnnouncesTheEventAtOnceOutsideATransaction() {
    when(caches.getCache(EventPages.CACHE)).thenReturn(pages);

    updates.announce(EVENT);

    verify(pages).evict(EVENT);
    verify(redis).convertAndSend(LiveEventConfiguration.CHANNEL, EVENT.toString());
  }

  @Test
  void waitsForTheCommitInsideATransaction() {
    when(caches.getCache(EventPages.CACHE)).thenReturn(pages);
    TransactionSynchronizationManager.initSynchronization();

    updates.announce(EVENT);

    verify(pages, never()).evict(EVENT);
    verify(redis, never()).convertAndSend(anyString(), anyString());
    for (TransactionSynchronization synchronization :
        TransactionSynchronizationManager.getSynchronizations()) {
      synchronization.afterCommit();
    }
    verify(pages).evict(EVENT);
    verify(redis).convertAndSend(LiveEventConfiguration.CHANNEL, EVENT.toString());
  }

  @Test
  void keepsGoingWhenRedisIsUnavailable() {
    when(caches.getCache(EventPages.CACHE)).thenReturn(pages);
    doThrow(new IllegalStateException("Redis is down")).when(pages).evict(EVENT);

    updates.announce(EVENT);

    verify(redis, never()).convertAndSend(anyString(), anyString());
  }
}
