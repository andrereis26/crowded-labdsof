package com.crowded.core.event.infrastructure.config;

import com.crowded.core.event.application.dto.EventPage;
import com.crowded.core.event.application.service.EventPages;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext.SerializationPair;
import tools.jackson.databind.json.JsonMapper;

/**
 * Event pages are read far more often than they change, so Core keeps them in Redis, as JSON,
 * shared by every instance (ADR 0004). A change drops the page at once; the expiry only bounds what
 * a missed drop could leave behind.
 */
@Configuration(proxyBeanMethods = false)
@EnableCaching
class EventPageCacheConfiguration {

  /** How long a page stays in the cache without a change. */
  static final Duration PAGE_TTL = Duration.ofMinutes(10);

  @Bean
  CacheManager cacheManager(RedisConnectionFactory redis, JsonMapper json) {
    RedisCacheConfiguration pages =
        RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(PAGE_TTL)
            .disableCachingNullValues()
            .prefixCacheNameWith("core:")
            .serializeValuesWith(
                SerializationPair.fromSerializer(
                    new JacksonJsonRedisSerializer<>(json, EventPage.class)));
    return RedisCacheManager.builder(redis)
        .withCacheConfiguration(EventPages.CACHE, pages)
        .disableCreateOnMissingCache()
        .build();
  }
}
