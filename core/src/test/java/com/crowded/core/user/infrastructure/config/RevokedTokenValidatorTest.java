package com.crowded.core.user.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.crowded.core.shared.domain.exception.UnavailableException;
import com.crowded.core.user.domain.repository.TokenRevocations;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class RevokedTokenValidatorTest {

  private static final UUID USER = UUID.randomUUID();
  private static final Instant REVOKED_AT = Instant.parse("2026-11-12T10:00:00.500Z");

  private final TokenRevocations revocations = mock(TokenRevocations.class);
  private final RevokedTokenValidator validator = new RevokedTokenValidator(revocations);

  @Test
  void acceptsATokenOfAUserWithoutRevocations() {
    when(revocations.revokedBefore(USER)).thenReturn(Optional.empty());

    assertThat(validator.validate(token(USER.toString(), REVOKED_AT, null)).hasErrors()).isFalse();
  }

  @Test
  void refusesATokenIssuedBeforeTheRevocationAndAcceptsOneIssuedAfter() {
    when(revocations.revokedBefore(USER)).thenReturn(Optional.of(REVOKED_AT));

    Instant before = REVOKED_AT.minusMillis(100);
    Instant after = REVOKED_AT.plusMillis(100);
    // Same second as the revocation: only iat_ms tells them apart.
    assertThat(validator.validate(token(USER.toString(), before, before)).hasErrors()).isTrue();
    assertThat(validator.validate(token(USER.toString(), after, after)).hasErrors()).isFalse();
    // Without iat_ms, iat (whole seconds) is before the revocation.
    assertThat(validator.validate(token(USER.toString(), after, null)).hasErrors()).isTrue();
  }

  @Test
  void refusesEveryTokenWhenTheRevocationsCannotBeRead() {
    when(revocations.revokedBefore(USER))
        .thenThrow(new UnavailableException("REVOCATIONS_UNAVAILABLE", "Down."));

    assertThat(validator.validate(token(USER.toString(), REVOKED_AT, null)).getErrors())
        .singleElement()
        .satisfies(e -> assertThat(e.getDescription()).contains("cannot be checked"));
  }

  @Test
  void leavesATokenWithoutAUserToTheEndpoints() {
    assertThat(validator.validate(token("not-a-user", REVOKED_AT, null)).hasErrors()).isFalse();
  }

  private static Jwt token(String subject, Instant issuedAt, Instant issuedAtMillis) {
    Jwt.Builder token =
        Jwt.withTokenValue("t")
            .header("alg", "RS256")
            .subject(subject)
            .issuedAt(Instant.ofEpochSecond(issuedAt.getEpochSecond()))
            .expiresAt(issuedAt.plusSeconds(3600));
    if (issuedAtMillis != null) {
      token.claim("iat_ms", issuedAtMillis.toEpochMilli());
    }
    return token.build();
  }
}
