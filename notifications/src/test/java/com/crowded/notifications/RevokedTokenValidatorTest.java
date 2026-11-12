package com.crowded.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.oauth2.jwt.Jwt;

class RevokedTokenValidatorTest {

  private static final UUID USER = UUID.randomUUID();
  private static final Instant REVOKED_AT = Instant.parse("2026-11-12T10:00:00.500Z");

  private final StringRedisTemplate redis = mock(StringRedisTemplate.class);

  @SuppressWarnings("unchecked")
  private final ValueOperations<String, String> values = mock(ValueOperations.class);

  private final RevokedTokenValidator validator = new RevokedTokenValidator(redis);

  @Test
  void comparesTheTokensMillisecondsWithTheRevocation() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("revoked-before:" + USER)).thenReturn(Long.toString(REVOKED_AT.toEpochMilli()));

    assertThat(
            validator.validate(token(USER.toString(), REVOKED_AT.minusMillis(1), true)).hasErrors())
        .isTrue();
    assertThat(
            validator.validate(token(USER.toString(), REVOKED_AT.plusMillis(1), true)).hasErrors())
        .isFalse();
    assertThat(
            validator.validate(token(USER.toString(), REVOKED_AT.plusMillis(1), false)).hasErrors())
        .isTrue();
  }

  @Test
  void acceptsAUserWithoutRevocationAndATokenWithoutAUser() {
    when(redis.opsForValue()).thenReturn(values);

    assertThat(validator.validate(token(USER.toString(), REVOKED_AT, true)).hasErrors()).isFalse();
    assertThat(validator.validate(token("service", REVOKED_AT, true)).hasErrors()).isFalse();
  }

  @Test
  void refusesEveryTokenWhenRedisIsDown() {
    when(redis.opsForValue()).thenReturn(values);
    when(values.get("revoked-before:" + USER))
        .thenThrow(new RedisConnectionFailureException("down"));

    assertThat(validator.validate(token(USER.toString(), REVOKED_AT, true)).hasErrors()).isTrue();
  }

  private static Jwt token(String subject, Instant issuedAt, boolean withMillis) {
    Jwt.Builder token =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .subject(subject)
            .issuedAt(Instant.ofEpochSecond(issuedAt.getEpochSecond()))
            .expiresAt(issuedAt.plusSeconds(3600));
    if (withMillis) {
      token.claim("iat_ms", issuedAt.toEpochMilli());
    }
    return token.build();
  }
}
