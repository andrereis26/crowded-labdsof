package com.crowded.core.user.infrastructure.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.crowded.core.shared.domain.exception.UnavailableException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TokenRevocationsRepositoryAdapterTest {

  private static final UUID USER = UUID.randomUUID();
  private static final Instant AT = Instant.parse("2026-11-12T10:00:00.250Z");

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  private final TokenRevocationsRepositoryAdapter adapter =
      new TokenRevocationsRepositoryAdapter(
          redis, Duration.ofHours(1), Clock.fixed(AT, ZoneOffset.UTC));

  @Test
  void keepsTheRevocationInMillisecondsUntilTheLastTokenExpires() {
    when(redis.opsForValue()).thenReturn(values);

    adapter.revokeIssuedBefore(USER, AT);

    verify(values)
        .set("revoked-before:" + USER, Long.toString(AT.toEpochMilli()), Duration.ofMinutes(61));
  }

  @Test
  void readsTheRevocationBack() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("revoked-before:" + USER)).thenReturn(Long.toString(AT.toEpochMilli()));

    assertThat(adapter.revokedBefore(USER)).contains(AT);
    assertThat(adapter.revokedBefore(UUID.randomUUID())).isEmpty();
  }

  @Test
  void failsSafeWhenRedisIsDown() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get(anyString())).thenThrow(new RedisConnectionFailureException("down"));
    org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));

    assertThatThrownBy(() -> adapter.revokedBefore(USER)).isInstanceOf(UnavailableException.class);
    assertThatThrownBy(() -> adapter.revokeIssuedBefore(USER, AT))
        .isInstanceOf(UnavailableException.class);
  }

  @Test
  void keepsASignedOutTokenUntilItWouldHaveExpired() {
    when(redis.opsForValue()).thenReturn(values);
    when(redis.hasKey("revoked-token:jti-1")).thenReturn(true);

    adapter.revokeToken("jti-1", AT.plus(Duration.ofMinutes(15)));
    adapter.revokeToken("expired", AT.minusSeconds(1));

    verify(values).set("revoked-token:jti-1", "1", Duration.ofMinutes(16));
    verify(values, org.mockito.Mockito.never())
        .set(
            org.mockito.ArgumentMatchers.eq("revoked-token:expired"),
            anyString(),
            any(Duration.class));
    assertThat(adapter.isTokenRevoked("jti-1")).isTrue();
    assertThat(adapter.isTokenRevoked("jti-2")).isFalse();
  }

  @Test
  void failsSafeWhenASignedOutTokenCannotBeStoredOrRead() {
    when(redis.opsForValue()).thenReturn(values);
    org.mockito.Mockito.doThrow(new RedisConnectionFailureException("down"))
        .when(values)
        .set(anyString(), anyString(), any(Duration.class));
    when(redis.hasKey(anyString())).thenThrow(new RedisConnectionFailureException("down"));

    assertThatThrownBy(() -> adapter.revokeToken("jti-1", AT.plusSeconds(60)))
        .isInstanceOf(UnavailableException.class);
    assertThatThrownBy(() -> adapter.isTokenRevoked("jti-1"))
        .isInstanceOf(UnavailableException.class);
  }
}
