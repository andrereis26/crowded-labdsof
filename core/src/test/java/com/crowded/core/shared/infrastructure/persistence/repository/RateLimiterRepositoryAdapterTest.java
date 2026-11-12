package com.crowded.core.shared.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.shared.domain.exception.UnavailableException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RateLimiterRepositoryAdapterTest {

  private static final String KEY = "rate-limit:sign-in:address:203.0.113.7";
  private static final Duration WINDOW = Duration.ofMinutes(15);

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  private final RateLimiterRepositoryAdapter limiter = new RateLimiterRepositoryAdapter(redis);

  @Test
  void startsTheWindowWithTheFirstAttemptAndCountsUpToTheLimit() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment(KEY)).thenReturn(1L, 2L, 3L);

    assertThat(limiter.tryAcquire("sign-in:address:203.0.113.7", 2, WINDOW)).isTrue();
    assertThat(limiter.tryAcquire("sign-in:address:203.0.113.7", 2, WINDOW)).isTrue();
    when(redis.getExpire(KEY)).thenReturn(800L);
    assertThat(limiter.tryAcquire("sign-in:address:203.0.113.7", 2, WINDOW)).isFalse();

    verify(redis).expire(KEY, WINDOW);
  }

  @Test
  void givesAnExpiryToACounterThatLostIt() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment(KEY)).thenReturn(5L);
    when(redis.getExpire(KEY)).thenReturn(-1L);

    assertThat(limiter.tryAcquire("sign-in:address:203.0.113.7", 2, WINDOW)).isFalse();

    verify(redis).expire(KEY, WINDOW);
  }

  @Test
  void refusesWhenRedisIsDown() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.increment(anyString())).thenThrow(new RedisConnectionFailureException("down"));

    assertThatThrownBy(() -> limiter.tryAcquire("x", 2, WINDOW))
        .isInstanceOf(UnavailableException.class);
    verify(redis, never()).expire(anyString(), any(Duration.class));
  }
}
